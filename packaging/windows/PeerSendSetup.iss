; Inno Setup script for the Windows PeerSend desktop engine installer.
; Update the Source paths to your packaged engine binaries before building.

#ifndef SourceRoot
  #define SourceRoot "..\..\dist\windows\dist\PeerSend"
#endif

#ifndef OutputDir
  #define OutputDir "."
#endif

#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif

[Setup]
AppId={{9E3911B7-1F0D-4A0C-9A02-2D9A6A708521}
AppName=PeerSend
AppVersion={#AppVersion}
AppPublisher=rhkr8521
DefaultDirName={autopf}\PeerSend
DefaultGroupName=PeerSend
OutputDir={#OutputDir}
OutputBaseFilename=PeerSend-Setup
Compression=lzma
SolidCompression=yes
ArchitecturesInstallIn64BitMode=x64

[Files]
Source: "{#SourceRoot}\PeerSend.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#SourceRoot}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\PeerSend"; Filename: "{app}\PeerSend.exe"
Name: "{autodesktop}\PeerSend"; Filename: "{app}\PeerSend.exe"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional icons:"

[Registry]
Root: HKCU; Subkey: "Software\Classes\peersend"; ValueType: string; ValueName: ""; ValueData: "URL:PeerSend Protocol"; Flags: uninsdeletekey
Root: HKCU; Subkey: "Software\Classes\peersend"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""; Flags: uninsdeletevalue
Root: HKCU; Subkey: "Software\Classes\peersend\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\PeerSend.exe,0"; Flags: uninsdeletekey
Root: HKCU; Subkey: "Software\Classes\peersend\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\PeerSend.exe"" ""%1"""; Flags: uninsdeletekey

[Run]
Filename: "{app}\PeerSend.exe"; Description: "Launch PeerSend"; Flags: nowait postinstall skipifsilent
