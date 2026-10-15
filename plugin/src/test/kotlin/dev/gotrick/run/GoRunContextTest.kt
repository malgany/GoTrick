package dev.gotrick.run

import org.junit.Assert.*
import org.junit.Test

class GoRunContextTest {
    @Test fun `main package runs`() {
        assertEquals(GoOperation.RUN, GoRunContext.infer("main.go", "package main\nfunc main() {}", 25)?.operation)
    }

    @Test fun `library package tests instead of attempting executable run`() {
        assertEquals(GoOperation.TEST, GoRunContext.infer("sum.go", "package sum\nfunc Sum() {}", 20)?.operation)
    }

    @Test fun `test context selects exact function`() {
        val source = "package sum\nfunc TestOne(t *testing.T) {}\nfunc TestTwo(t *testing.T) {}"
        assertEquals("TestTwo", GoRunContext.infer("sum_test.go", source, source.indexOf("TestTwo"))?.function)
    }

    @Test fun `test helper does not select previous test`() {
        val source = "package sum\nfunc TestOne(t *testing.T) {}\nfunc helper() {}"
        assertEquals("", GoRunContext.infer("sum_test.go", source, source.indexOf("helper"))?.function)
    }

    @Test fun `comments and literal contents cannot create fake declarations`() {
        val source = "package sum\n/*\npackage main\nfunc TestFake() {}\n*/\nvar s = `\npackage main\n`"
        assertEquals(GoOperation.TEST, GoRunContext.infer("sum.go", source, source.length)?.operation)
        assertEquals("", GoRunContext.infer("sum_test.go", source, source.length)?.function)
    }

    @Test fun `test names follow Go naming boundary`() {
        assertTrue(GoRunContext.isRunnableFunction("x_test.go", "Test"))
        assertTrue(GoRunContext.isRunnableFunction("x_test.go", "Test1"))
        assertTrue(GoRunContext.isRunnableFunction("x_test.go", "Example"))
        assertFalse(GoRunContext.isRunnableFunction("x_test.go", "Testhelper"))
        assertFalse(GoRunContext.isRunnableFunction("x.go", "TestFoo"))
    }

    @Test fun `non Go files have no context`() { assertNull(GoRunContext.infer("README.md", "package main", 0)) }
}
