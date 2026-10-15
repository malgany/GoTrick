#ifndef AppVersion
  #error AppVersion is required
#endif
#ifndef StageDir
  #error StageDir is required
#endif
#ifndef OutputDir
  #error OutputDir is required
#endif
[Setup]
AppId={{3E71A5F8-EE21-44C5-843B-839FDCA4CC75}
AppName=GoTrick
AppVersion={#AppVersion}
AppPublisher=GoTrick contributors
DefaultDirName={localappdata}\Programs\GoTrick
DefaultGroupName=GoTrick
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
OutputDir={#OutputDir}
OutputBaseFilename=GoTrick-{#AppVersion}-windows-x64-setup
SetupIconFile={#StageDir}\ide\bin\GoTrick.ico
UninstallDisplayIcon={app}\ide\bin\gotrick64.exe
LicenseFile={#StageDir}\LICENSE
Compression=lzma2/fast
SolidCompression=yes
WizardStyle=modern
AllowNoIcons=yes
CloseApplications=no
RestartApplications=no
UninstallDisplayName=GoTrick
[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "brazilianportuguese"; MessagesFile: "compiler:Languages\BrazilianPortuguese.isl"
[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
[Files]
Source: "{#StageDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
[Icons]
Name: "{group}\GoTrick"; Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\launch.ps1"""; WorkingDir: "{app}"; IconFilename: "{app}\ide\bin\GoTrick.ico"
Name: "{autodesktop}\GoTrick"; Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\launch.ps1"""; WorkingDir: "{app}"; IconFilename: "{app}\ide\bin\GoTrick.ico"; Tasks: desktopicon
[Run]
Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\launch.ps1"""; Description: "{cm:LaunchProgram,GoTrick}"; Flags: nowait postinstall skipifsilent
