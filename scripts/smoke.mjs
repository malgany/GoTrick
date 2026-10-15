import { spawn, execFileSync } from 'node:child_process';
import { mkdir, writeFile, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import net from 'node:net';
import assert from 'node:assert/strict';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const tools = path.join(root, '.tools');
const env = { ...process.env, GOTOOLCHAIN: 'local', GOCACHE: path.join(tools, 'go-cache'), GOPATH: path.join(tools, 'go-work'), PATH: `${path.join(tools, 'go', 'bin')}${path.delimiter}${process.env.PATH}` };
const go = path.join(tools, 'go', 'bin', 'go.exe');
const gopls = path.join(tools, 'gotools', 'gopls.exe');
const delve = path.join(tools, 'gotools', 'dlv.exe');
const fixture = path.join(root, 'build', 'smoke', `Go project ação ${Date.now()}`);
const report = { at: new Date().toISOString(), checks: [], versions: {} };
const children = new Set();
function check(name, details) { report.checks.push({ name, passed: true, details }); console.log(`PASS ${name}`); }
function run(exe, args, cwd = fixture) { return execFileSync(exe, args, { cwd, env, encoding: 'utf8', timeout: 120000, windowsHide: true }); }

class Protocol {
  constructor(readable, writable, mode) {
    this.writer = writable; this.mode = mode; this.buffer = Buffer.alloc(0); this.id = 0;
    this.pending = new Map(); this.events = []; this.handlers = [];
    readable.on('data', data => this.feed(data));
  }
  feed(data) {
    this.buffer = Buffer.concat([this.buffer, data]);
    for (;;) {
      const boundary = this.buffer.indexOf('\r\n\r\n'); if (boundary < 0) return;
      const match = /Content-Length:\s*(\d+)/i.exec(this.buffer.subarray(0, boundary).toString());
      if (!match) throw new Error('Invalid protocol header');
      const length = Number(match[1]); if (this.buffer.length < boundary + 4 + length) return;
      const msg = JSON.parse(this.buffer.subarray(boundary + 4, boundary + 4 + length).toString('utf8'));
      this.buffer = this.buffer.subarray(boundary + 4 + length);
      const responseId = this.mode === 'dap' ? msg.request_seq : msg.id;
      if ((this.mode === 'dap' ? msg.type === 'response' : !msg.method && msg.id != null) && this.pending.has(responseId)) {
        const item = this.pending.get(responseId); this.pending.delete(responseId); clearTimeout(item.timer);
        if (msg.error || msg.success === false) item.reject(new Error(JSON.stringify(msg.error ?? msg)));
        else item.resolve(this.mode === 'dap' ? msg.body : msg.result);
      } else if (this.mode === 'lsp' && msg.id != null && msg.method) {
        const result = msg.method === 'workspace/configuration' ? msg.params.items.map(() => ({})) : null;
        this.send({ jsonrpc: '2.0', id: msg.id, result });
      } else {
        this.events.push(msg);
        for (const handler of [...this.handlers]) handler();
      }
    }
  }
  send(message) { const body = Buffer.from(JSON.stringify(message)); this.writer.write(`Content-Length: ${body.length}\r\n\r\n`); this.writer.write(body); }
  notify(method, params) { this.send({ jsonrpc: '2.0', method, params }); }
  request(method, params, timeout = 60000) {
    const id = ++this.id;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { this.pending.delete(id); reject(new Error(`Timed out: ${method}`)); }, timeout);
      this.pending.set(id, { resolve, reject, timer });
      this.send(this.mode === 'dap' ? { seq: id, type: 'request', command: method, arguments: params } : { jsonrpc: '2.0', id, method, params });
    });
  }
  event(predicate, timeout = 60000) {
    return new Promise((resolve, reject) => {
      const inspect = () => {
        const index = this.events.findIndex(predicate);
        if (index >= 0) { clearTimeout(timer); this.handlers = this.handlers.filter(h => h !== inspect); resolve(this.events.splice(index, 1)[0]); }
      };
      const timer = setTimeout(() => { this.handlers = this.handlers.filter(h => h !== inspect); reject(new Error('Expected event did not arrive')); }, timeout);
      this.handlers.push(inspect); inspect();
    });
  }
  dispose() { for (const entry of this.pending.values()) clearTimeout(entry.timer); this.pending.clear(); }
}

function start(exe, args) {
  const process = spawn(exe, args, { cwd: fixture, env, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  children.add(process); process.once('exit', () => children.delete(process)); return process;
}
function position(text, needle, delta = 0) {
  const offset = text.indexOf(needle) + delta; assert.ok(offset >= 0);
  const before = text.slice(0, offset).split('\n'); return { line: before.length - 1, character: before.at(-1).length };
}

await mkdir(fixture, { recursive: true });
const source = 'package main\n\nimport "fmt"\n\nfunc Greeting(name string) string {\nreturn "Hello, " + name + "!"\n}\n\nfunc main() {\nmessage := Greeting("GoTrick")\nfmt.Println(message)\n}\n';
const file = path.join(fixture, 'main.go'); const uri = pathToFileURL(file).href;
await writeFile(path.join(fixture, 'go.mod'), 'module example.com/gotrick/smoke\n\ngo 1.22\n');
await writeFile(file, source);
await writeFile(path.join(fixture, 'main_test.go'), 'package main\nimport "testing"\nfunc TestGreeting(t *testing.T) { if Greeting("GoTrick") != "Hello, GoTrick!" { t.Fatal("bad greeting") } }\n');
try {
  report.versions.go = run(go, ['version']); report.versions.gopls = run(gopls, ['version']); report.versions.delve = run(delve, ['version']);
  assert.match(run(go, ['test', './...']), /ok\s/); check('go test');
  run(go, ['vet', './...']); check('go vet');
  run(go, ['build', '-o', 'sample.exe', '.']); assert.match(run(path.join(fixture, 'sample.exe'), []), /Hello, GoTrick!/); check('build and run (space + Unicode path)');

  const server = start(gopls, ['serve']); let stderr = ''; server.stderr.on('data', b => { stderr += b; });
  const lsp = new Protocol(server.stdout, server.stdin, 'lsp');
  const initialized = await lsp.request('initialize', { processId: process.pid, rootUri: pathToFileURL(fixture).href, workspaceFolders: [{ uri: pathToFileURL(fixture).href, name: 'smoke' }], capabilities: { textDocument: { completion: { completionItem: { snippetSupport: true } } }, workspace: { configuration: true, workspaceFolders: true } } });
  assert.ok(initialized.capabilities.definitionProvider); lsp.notify('initialized', {});
  lsp.notify('textDocument/didOpen', { textDocument: { uri, languageId: 'go', version: 1, text: source } });
  const completion = await lsp.request('textDocument/completion', { textDocument: { uri }, position: position(source, 'fmt.Println', 6) });
  assert.ok((completion.items ?? completion).some(item => item.label.includes('Println'))); check('gopls completion');
  const callPosition = position(source, 'Greeting("GoTrick")', 3);
  const definitions = await lsp.request('textDocument/definition', { textDocument: { uri }, position: callPosition });
  assert.ok(definitions.length > 0); check('gopls definition');
  const references = await lsp.request('textDocument/references', { textDocument: { uri }, position: callPosition, context: { includeDeclaration: true } });
  assert.ok(references.length >= 2); check('gopls references');
  const rename = await lsp.request('textDocument/rename', { textDocument: { uri }, position: callPosition, newName: 'Salutation' });
  const edits = rename.documentChanges?.flatMap(change => change.edits ?? []) ?? Object.values(rename.changes ?? {}).flat();
  assert.ok(edits.length >= 2 && edits.every(edit => edit.newText === 'Salutation')); check('gopls rename across files');
  const formatting = await lsp.request('textDocument/formatting', { textDocument: { uri }, options: { tabSize: 4, insertSpaces: false } });
  assert.ok(formatting.length); check('gopls formatting');
  lsp.notify('textDocument/didChange', { textDocument: { uri, version: 2 }, contentChanges: [{ text: source + '\nvar broken = doesNotExist\n' }] });
  const diagnostic = await lsp.event(msg => msg.method === 'textDocument/publishDiagnostics' && msg.params.uri === uri && msg.params.diagnostics.some(d => d.message.includes('doesNotExist')));
  check('gopls unsaved-document diagnostics', diagnostic.params.diagnostics.map(d => d.message));
  await lsp.request('shutdown', null); lsp.notify('exit'); lsp.dispose(); server.stdin.end();

  const debug = start(delve, ['dap', '--listen=127.0.0.1:0']);
  const address = await new Promise((resolve, reject) => {
    let log = ''; const timer = setTimeout(() => reject(new Error('Delve startup timeout: ' + log)), 30000);
    const collect = data => { log += data; const match = /DAP server listening at:\s*127\.0\.0\.1:(\d+)/.exec(log); if (match) { clearTimeout(timer); resolve(Number(match[1])); } };
    debug.stdout.on('data', collect); debug.stderr.on('data', collect);
    debug.once('error', reject);
  });
  const socket = net.connect({ host: '127.0.0.1', port: address });
  await new Promise((resolve, reject) => { socket.once('connect', resolve); socket.once('error', reject); });
  const dap = new Protocol(socket, socket, 'dap');
  await dap.request('initialize', { clientID: 'gotrick-smoke', adapterID: 'go', pathFormat: 'path', linesStartAt1: true, columnsStartAt1: true, supportsVariableType: true });
  const launch = dap.request('launch', { name: 'GoTrick Smoke', type: 'go', request: 'launch', mode: 'debug', program: fixture, cwd: fixture, stopOnEntry: false });
  await dap.event(msg => msg.event === 'initialized');
  const breakpoint = await dap.request('setBreakpoints', { source: { path: file }, breakpoints: [{ line: 11 }] });
  assert.ok(breakpoint.breakpoints[0].verified);
  await dap.request('configurationDone', {}); await launch;
  const stopped = await dap.event(msg => msg.event === 'stopped');
  const stack = await dap.request('stackTrace', { threadId: stopped.body.threadId });
  assert.ok(stack.stackFrames.some(frame => frame.name.includes('main.main')));
  const scopes = await dap.request('scopes', { frameId: stack.stackFrames[0].id });
  const vars = await dap.request('variables', { variablesReference: scopes.scopes[0].variablesReference });
  assert.ok(vars.variables.some(variable => variable.name === 'message' && variable.value.includes('GoTrick')));
  assert.equal(stopped.body.reason, 'breakpoint');
  check('Delve breakpoint, stack and local variables', {
    reason: stopped.body.reason,
    breakpointLine: breakpoint.breakpoints[0].line,
    frame: stack.stackFrames[0].name,
    message: vars.variables.find(variable => variable.name === 'message')?.value
  });
  await dap.request('continue', { threadId: stopped.body.threadId });
  await dap.event(msg => msg.event === 'terminated');
  check('Delve continue to program termination');
  await dap.request('disconnect', { terminateDebuggee: true }); dap.dispose(); socket.end();
  report.passed = true;
} catch (error) {
  report.passed = false; report.error = error.stack; console.error(error); process.exitCode = 1;
} finally {
  for (const child of children) child.kill();
  await mkdir(path.join(root, 'validation'), { recursive: true });
  await writeFile(path.join(root, 'validation', 'backend-smoke.json'), JSON.stringify(report, null, 2));
}
