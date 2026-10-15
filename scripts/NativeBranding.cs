using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;

// Changes resources only in the private, copied launcher. No executable code is patched.
public static class GoTrickNativeBranding {
    private delegate bool EnumName(IntPtr module, IntPtr type, IntPtr name, IntPtr parameter);
    private delegate bool EnumLanguage(IntPtr module, IntPtr type, IntPtr name, ushort language, IntPtr parameter);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] private static extern IntPtr LoadLibraryEx(string path, IntPtr file, uint flags);
    [DllImport("kernel32.dll")] private static extern bool FreeLibrary(IntPtr module);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] private static extern bool EnumResourceNames(IntPtr module, IntPtr type, EnumName callback, IntPtr parameter);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] private static extern bool EnumResourceLanguages(IntPtr module, IntPtr type, IntPtr name, EnumLanguage callback, IntPtr parameter);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] private static extern IntPtr BeginUpdateResource(string path, bool deleteExisting);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] private static extern bool UpdateResource(IntPtr handle, IntPtr type, IntPtr name, ushort language, byte[] data, uint length);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] private static extern bool EndUpdateResource(IntPtr handle, bool discard);
    private sealed class Resource { public int Type; public string Name; public ushort Language; }
    private static string NameOf(IntPtr name) { return name.ToInt64() <= 65535 ? "#" + name.ToInt64() : Marshal.PtrToStringUni(name); }
    private static void Put(IntPtr update, int type, string name, ushort language, byte[] data) {
        bool allocated = !name.StartsWith("#", StringComparison.Ordinal);
        IntPtr pointer = allocated ? Marshal.StringToHGlobalUni(name) : new IntPtr(int.Parse(name.Substring(1)));
        try { if (!UpdateResource(update, new IntPtr(type), pointer, language, data, (uint)data.Length)) throw new Win32Exception(); }
        finally { if (allocated) Marshal.FreeHGlobal(pointer); }
    }
    public static void Apply(string executable, string icon, string version) {
        string copyright = FileVersionInfo.GetVersionInfo(executable).LegalCopyright ?? "Copyright JetBrains s.r.o. and contributors";
        var resources = new List<Resource>();
        IntPtr module = LoadLibraryEx(executable, IntPtr.Zero, 0x22);
        if (module == IntPtr.Zero) throw new Win32Exception();
        try {
            foreach (int kind in new[] {14,16}) {
                EnumResourceNames(module, new IntPtr(kind), (m,t,n,p) => {
                    string resourceName = NameOf(n);
                    EnumResourceLanguages(m,t,n,(mm,tt,nn,language,pp) => { resources.Add(new Resource { Type=kind, Name=resourceName, Language=language }); return true; },IntPtr.Zero);
                    return true;
                },IntPtr.Zero);
            }
        } finally { FreeLibrary(module); }
        byte[] ico = File.ReadAllBytes(icon);
        ushort count = BitConverter.ToUInt16(ico,4);
        if (BitConverter.ToUInt16(ico,2)!=1 || count==0) throw new InvalidDataException("Invalid ICO");
        byte[] group;
        using (var buffer = new MemoryStream()) using (var writer = new BinaryWriter(buffer)) {
            writer.Write((ushort)0); writer.Write((ushort)1); writer.Write(count);
            for (int i=0;i<count;i++) { writer.Write(ico,6+i*16,12); writer.Write((ushort)(2100+i)); }
            group=buffer.ToArray();
        }
        // JBR loads group 2000 directly. Replace all existing groups as well for Explorer.
        resources.Add(new Resource { Type=14, Name="#2000", Language=0 });
        if (!resources.Exists(r=>r.Type==16)) resources.Add(new Resource { Type=16, Name="#1", Language=0 });
        IntPtr update = BeginUpdateResource(executable,false);
        if (update==IntPtr.Zero) throw new Win32Exception();
        bool complete=false;
        try {
            foreach (var resource in resources) {
                if (resource.Type==14) {
                    for (int i=0;i<count;i++) {
                        int length=BitConverter.ToInt32(ico,6+i*16+8), offset=BitConverter.ToInt32(ico,6+i*16+12);
                        byte[] frame=new byte[length]; Array.Copy(ico,offset,frame,0,length);
                        Put(update,3,"#"+(2100+i),resource.Language,frame);
                    }
                    Put(update,14,resource.Name,resource.Language,group);
                } else Put(update,16,resource.Name,resource.Language,Version(copyright,version));
            }
            complete=true;
        } finally { if (!EndUpdateResource(update,!complete) && complete) throw new Win32Exception(); }
    }
    private static void Align(BinaryWriter writer) { while (writer.BaseStream.Position%4!=0) writer.Write((byte)0); }
    private static byte[] Block(string key, ushort type, byte[] value, ushort valueLength, params byte[][] children) {
        using (var stream=new MemoryStream()) using(var writer=new BinaryWriter(stream)) {
            writer.Write((ushort)0); writer.Write(valueLength); writer.Write(type);
            writer.Write(Encoding.Unicode.GetBytes(key+"\0")); Align(writer); writer.Write(value);
            foreach(var child in children) { Align(writer); writer.Write(child); }
            ushort length=checked((ushort)stream.Length); stream.Position=0; writer.Write(length); return stream.ToArray();
        }
    }
    private static byte[] Text(string name,string value) { return Block(name,1,Encoding.Unicode.GetBytes(value+"\0"),(ushort)(value.Length+1)); }
    private static byte[] Version(string copyright, string version) {
        var numbers = System.Version.Parse(version);
        var fields=new[] { Text("CompanyName","GoTrick"), Text("FileDescription","GoTrick"), Text("ProductName","GoTrick"),
            Text("FileVersion",version+".0"), Text("ProductVersion",version), Text("InternalName","GoTrick"),
            Text("OriginalFilename","gotrick64.exe"), Text("LegalCopyright",copyright+"; GoTrick branding 2026") };
        byte[] fixedInfo;
        using(var stream=new MemoryStream()) using(var writer=new BinaryWriter(stream)) {
            uint high = (uint)((numbers.Major << 16) | numbers.Minor), low = (uint)(numbers.Build << 16);
            foreach(uint value in new uint[] {0xFEEF04BD,0x10000,high,low,high,low,0x3F,0,0x40004,1,0,0,0}) writer.Write(value);
            fixedInfo=stream.ToArray();
        }
        return Block("VS_VERSION_INFO",0,fixedInfo,52,
            Block("StringFileInfo",1,new byte[0],0,Block("040904b0",1,new byte[0],0,fields)),
            Block("VarFileInfo",1,new byte[0],0,Block("Translation",0,new byte[] {9,4,176,4},4)));
    }
}
