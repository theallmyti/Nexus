; Nexus agent installer (Inno Setup 6).
; Build: see installer/README.md. Output: dist\nexus-setup-<version>.exe
;
; Install (docs/Architecture.md §2.3a):
; - Asks for admin (UAC) like any installer; any local fixed drive works as the install folder
; - Locks the install folder to SYSTEM + Administrators (the service runs as LocalSystem)
; - Optional: installs the official PawnIO driver for CPU temperature (full mode, F8)
; - Writes %ProgramData%\Nexus\config.toml (SYSTEM + Administrators only, docs/Security.md §6)
; - "Pair this computer" page: QR code image, code, countdown; the owner scans it on the site/app
;   (docs/Flow.md §3). A new code appears automatically when one expires.
; - Creates the "NexusAgent" Windows service (restarted after a crash, NFR-06) and starts it
;   once pairing is done or skipped
; Uninstall: stops and deletes the service, removes the device and its history from Nexus,
; removes PawnIO (silently if this installer added it, otherwise after asking), deletes all files.

#define AppName "Nexus Agent"
#define AppVersion "0.2.3"
#define ServiceName "NexusAgent"
; The Convex URL is kept out of the repo: installer/convex-url.txt (git-ignored, one line).
; Test builds: ISCC /DChannel=dev reads installer/convex-url-dev.txt and adds "-dev" to the name.
#ifdef Channel
  #define UrlFile AddBackslash(SourcePath) + "convex-url-" + Channel + ".txt"
  #define OutSuffix "-" + Channel
#else
  #define UrlFile AddBackslash(SourcePath) + "convex-url.txt"
  #define OutSuffix ""
#endif
#if !FileExists(UrlFile)
  #error The Convex URL file (installer/convex-url.txt, or convex-url-<channel>.txt) is missing. It is not committed.
#endif
#define UrlHandle FileOpen(UrlFile)
#define ConvexUrl Trim(FileRead(UrlHandle))
#expr FileClose(UrlHandle)
#if Pos(".convex.cloud", ConvexUrl) == 0
  #error installer/convex-url.txt must hold a Convex URL like https://<deployment>.convex.cloud
#endif
#define StateKey "SOFTWARE\Nexus"

[Setup]
AppId={{F9FF55E1-801A-4E78-96FA-550A8B46C103}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=Aditya Prasad, Tirth Patel, Pratham Vasava
AppPublisherURL=https://github.com/theallmyti/Nexus
DefaultDirName={autopf}\Nexus
DefaultGroupName=Nexus
DisableProgramGroupPage=yes
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=..\dist
OutputBaseFilename=nexus-setup-{#AppVersion}{#OutSuffix}
Compression=lzma2
SolidCompression=yes
; No licence page: consent is a checkbox on the first screen (links open the full documents).
DisableWelcomePage=no
DisableReadyPage=yes
WizardStyle=modern
; Nexus theme: pixel-art side image and logo (installer/assets/make_wizard_art.py), 100/150/200 % DPI
WizardImageFile=assets\wizard-image-100.bmp,assets\wizard-image-125.bmp,assets\wizard-image-150.bmp,assets\wizard-image-175.bmp,assets\wizard-image-200.bmp
WizardSmallImageFile=assets\wizard-small-100.bmp,assets\wizard-small-125.bmp,assets\wizard-small-150.bmp,assets\wizard-small-175.bmp,assets\wizard-small-200.bmp
; Fill the whole side panel (no white frame); any leftover edge is navy, not white.
WizardImageStretch=yes
WizardImageBackColor=$2E1B0F
UninstallDisplayName={#AppName}
UninstallDisplayIcon={app}\nexus.exe
SetupLogging=yes
CloseApplications=no

[Tasks]
Name: "cputemp"; Description: "Show CPU temperature (installs the PawnIO driver; the agent then runs with admin rights)"
Name: "tray"; Description: "Start Nexus in System Tray on login (uncheck to save ~15 MB RAM; control via terminal 'nexus' commands)"; Flags: checkedonce
Name: "addtopath"; Description: "Add Nexus to system PATH (allows running 'nexus' directly in Command Prompt or PowerShell)"; Flags: checkedonce

[Files]
Source: "..\agent\target\release\nexus.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\LICENSE"; DestDir: "{app}"; DestName: "LICENSE.txt"
Source: "LEGAL_AGREEMENT.txt"; DestDir: "{app}\docs"; Flags: ignoreversion
Source: "LEGAL_AGREEMENT.rtf"; DestDir: "{app}\docs"; Flags: ignoreversion
; Opened from the first screen before anything is installed.
Source: "LEGAL_AGREEMENT.rtf"; Flags: dontcopy
; Welcome/Finish animation frames (man waves, cat blinks), drawn by assets\make_wizard_art.py
Source: "assets\anim-*.bmp"; Flags: dontcopy
Source: "..\LEGAL.md"; DestDir: "{app}\docs"; Flags: ignoreversion
Source: "..\docs\PRIVACY.md"; DestDir: "{app}\docs"; Flags: ignoreversion
Source: "..\docs\TERMS.md"; DestDir: "{app}\docs"; Flags: ignoreversion
Source: "..\docs\COOKIE_POLICY.md"; DestDir: "{app}\docs"; Flags: ignoreversion
; Signed PawnIO modules (LGPL-2.1, github.com/namazso/PawnIO.Modules), fetched by fetch-pawnio.ps1
Source: "vendor\pawnio\AMDFamily17.bin"; DestDir: "{app}\pawnio"; Flags: ignoreversion
Source: "vendor\pawnio\IntelMSR.bin"; DestDir: "{app}\pawnio"; Flags: ignoreversion
Source: "vendor\pawnio\COPYING"; DestDir: "{app}\pawnio"; DestName: "COPYING-PawnIO-Modules.txt"
; Official signed PawnIO driver installer (github.com/namazso/PawnIO.Setup)
Source: "vendor\pawnio\PawnIO_setup.exe"; DestDir: "{tmp}"; Tasks: cputemp; Flags: deleteafterinstall

[Messages]
WelcomeLabel1=Install Nexus
WelcomeLabel2=Keep an eye on this computer's temperatures, memory and alerts from your phone.%n%nNexus runs quietly in the background and uses very little memory.
FinishedHeadingLabel=Nexus is ready
FinishedLabel=The agent is running in the background. Open the Nexus app on your phone to see this computer.
FinishedLabelNoIcons=The agent is running in the background. Open the Nexus app on your phone to see this computer.
ClickFinish=
ClickNext=
SelectDirLabel3=Nexus will be installed in this folder.
SelectDirBrowseLabel=
SelectTasksLabel2=Choose what Nexus should set up:

[Icons]
Name: "{group}\Uninstall Nexus Agent"; Filename: "{uninstallexe}"
Name: "{autostartup}\Nexus Agent Tray"; Filename: "{app}\nexus.exe"; Parameters: "tray"; Tasks: tray

[Run]
Filename: "{app}\nexus.exe"; Parameters: "tray"; Description: "Launch Nexus Agent in System Tray"; Flags: nowait postinstall skipifsilent runhidden; Tasks: tray

[Registry]
Root: HKLM; Subkey: "SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\nexus.exe"; ValueType: string; ValueName: ""; ValueData: "{app}\nexus.exe"; Flags: uninsdeletekey
Root: HKLM; Subkey: "SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\nexus.exe"; ValueType: string; ValueName: "Path"; ValueData: "{app}"; Flags: uninsdeletekey

[UninstallDelete]
; Config (with the device secret), logs and pairing files.
Type: filesandordirs; Name: "{commonappdata}\Nexus"
Type: filesandordirs; Name: "{commonappdata}\PCMonitor"

[Code]
function DataDir(): String;
begin
  Result := ExpandConstant('{commonappdata}\Nexus');
end;

function LegacyDataDir(): String;
begin
  Result := ExpandConstant('{commonappdata}\PCMonitor');
end;

function PairDir(): String;
begin
  Result := DataDir() + '\pair';
end;

function ConfigExists(): Boolean;
begin
  Result := FileExists(DataDir() + '\config.toml');
end;

function PawnIOInstalled(): Boolean;
begin
  Result := RegKeyExists(HKLM, 'SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\PawnIO');
end;

function ServiceExists(): Boolean;
var
  Code: Integer;
begin
  Result := Exec(ExpandConstant('{sys}\sc.exe'), 'query {#ServiceName}', '', SW_HIDE, ewWaitUntilTerminated, Code) and (Code = 0);
end;

procedure Sc(Params: String);
var
  Code: Integer;
begin
  Exec(ExpandConstant('{sys}\sc.exe'), Params, '', SW_HIDE, ewWaitUntilTerminated, Code);
  Log('sc.exe ' + Params + ' -> ' + IntToStr(Code));
end;

procedure AddToPath(Dir: String);
var
  OldPath, AppDir: String;
begin
  AppDir := RemoveQuotes(Dir);
  if RegQueryStringValue(HKLM, 'SYSTEM\CurrentControlSet\Control\Session Manager\Environment', 'Path', OldPath) then
  begin
    if Pos(';' + Uppercase(AppDir) + ';', ';' + Uppercase(OldPath) + ';') = 0 then
    begin
      if (Length(OldPath) > 0) and (OldPath[Length(OldPath)] <> ';') then
        OldPath := OldPath + ';';
      RegWriteStringValue(HKLM, 'SYSTEM\CurrentControlSet\Control\Session Manager\Environment', 'Path', OldPath + AppDir);
    end;
  end;
end;

procedure RemoveFromPath(Dir: String);
var
  OldPath, AppDir, Pattern: String;
  P: Integer;
begin
  AppDir := RemoveQuotes(Dir);
  if RegQueryStringValue(HKLM, 'SYSTEM\CurrentControlSet\Control\Session Manager\Environment', 'Path', OldPath) then
  begin
    Pattern := ';' + Uppercase(AppDir) + ';';
    P := Pos(Pattern, ';' + Uppercase(OldPath) + ';');
    if P > 0 then
    begin
      OldPath := ';' + OldPath + ';';
      StringChangeEx(OldPath, ';' + AppDir + ';', ';', True);
      if Length(OldPath) >= 2 then
        OldPath := Copy(OldPath, 2, Length(OldPath) - 2)
      else
        OldPath := '';
      RegWriteStringValue(HKLM, 'SYSTEM\CurrentControlSet\Control\Session Manager\Environment', 'Path', OldPath);
    end;
  end;
end;

// Only SYSTEM and Administrators may change these folders: the agent runs as LocalSystem, so a
// user-writable folder (common outside Program Files, e.g. on E:) would let anyone replace it.
procedure LockDir(Dir: String; UsersCanRead: Boolean);
var
  Code: Integer;
  Grants: String;
begin
  Grants := '*S-1-5-18:(OI)(CI)F *S-1-5-32-544:(OI)(CI)F';
  if UsersCanRead then
    Grants := Grants + ' *S-1-5-32-545:(OI)(CI)RX';
  Exec(ExpandConstant('{sys}\icacls.exe'), '"' + Dir + '" /inheritance:r /grant:r ' + Grants,
    '', SW_HIDE, ewWaitUntilTerminated, Code);
  Log('icacls ' + Dir + ' -> ' + IntToStr(Code));
end;

// Stop a running agent before its files are replaced (upgrade).
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  Code: Integer;
begin
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /IM nexus.exe', '', SW_HIDE, ewWaitUntilTerminated, Code);
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /IM pc-monitor-agent.exe', '', SW_HIDE, ewWaitUntilTerminated, Code);
  if ServiceExists() then
    Sc('stop {#ServiceName}');
  Exec(ExpandConstant('{sys}\sc.exe'), 'stop PCMonitorAgent', '', SW_HIDE, ewWaitUntilTerminated, Code);
  Result := '';
end;

// The agent adds its device secret on first run. An upgrade keeps the existing file (and so the
// device secret) but points it at this build's backend; a computer moved to another backend is
// then simply paired again on the next page.
procedure WriteConfig();
var
  Lines: TArrayOfString;
  I: Integer;
begin
  ForceDirectories(DataDir());
  // Migrate legacy config if upgrading from %ProgramData%\PCMonitor
  if (not ConfigExists()) and FileExists(LegacyDataDir() + '\config.toml') then
    FileCopy(LegacyDataDir() + '\config.toml', DataDir() + '\config.toml', False);

  if not ConfigExists() then
  begin
    SaveStringToFile(DataDir() + '\config.toml',
      '# Nexus agent config, written by the installer.' + #13#10 +
      'convex_url = "{#ConvexUrl}"' + #13#10 +
      'live_interval_secs = 1' + #13#10, False);
    Exit;
  end;
  if not LoadStringsFromFile(DataDir() + '\config.toml', Lines) then
    Exit;
  for I := 0 to GetArrayLength(Lines) - 1 do
    if Pos('convex_url', Trim(Lines[I])) = 1 then
      Lines[I] := 'convex_url = "{#ConvexUrl}"';
  SaveStringsToFile(DataDir() + '\config.toml', Lines, False);
end;

// Install PawnIO when asked and not already present; remember that we added it, so the
// uninstaller can remove it without affecting other apps that rely on PawnIO.
procedure InstallPawnIO();
var
  Code: Integer;
begin
  if not WizardIsTaskSelected('cputemp') or PawnIOInstalled() then
    Exit;
  WizardForm.StatusLabel.Caption := 'Installing the PawnIO driver...';
  Exec(ExpandConstant('{tmp}\PawnIO_setup.exe'), '-install -silent', '', SW_HIDE, ewWaitUntilTerminated, Code);
  Log('PawnIO setup -> ' + IntToStr(Code));
  if PawnIOInstalled() then
    RegWriteDWordValue(HKLM, '{#StateKey}', 'InstalledPawnIO', 1);
end;

// Created here, started after the pairing page so the agent starts as a paired device.
procedure CreateService();
var
  Code: Integer;
begin
  if not ServiceExists() then
  begin
    Sc('create {#ServiceName} binPath= "\"' + ExpandConstant('{app}') + '\nexus.exe\" service" start= delayed-auto DisplayName= "{#AppName}"');
    Sc('description {#ServiceName} "Sends this computer''s hardware stats to Nexus."');
    // Restart after a crash: 5 s, 5 s, then 60 s; reset the count after a day (NFR-06).
    Sc('failure {#ServiceName} reset= 86400 actions= restart/5000/restart/5000/restart/60000');
  end;
  // If upgrading from legacy PCMonitorAgent, stop and delete it
  Exec(ExpandConstant('{sys}\sc.exe'), 'stop PCMonitorAgent', '', SW_HIDE, ewWaitUntilTerminated, Code);
  Exec(ExpandConstant('{sys}\sc.exe'), 'delete PCMonitorAgent', '', SW_HIDE, ewWaitUntilTerminated, Code);
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  Code: Integer;
begin
  if CurStep = ssPostInstall then
  begin
    LockDir(ExpandConstant('{app}'), True);
    WriteConfig();
    LockDir(DataDir(), True);
    SaveStringToFile(DataDir() + '\control.json', '{"paused":false}', False);
    Exec(ExpandConstant('{sys}\icacls.exe'), '"' + DataDir() + '\control.json" /grant *S-1-5-32-545:M', '', SW_HIDE, ewWaitUntilTerminated, Code);
    Exec(ExpandConstant('{sys}\icacls.exe'), '"' + DataDir() + '\status.json" /grant *S-1-5-32-545:M', '', SW_HIDE, ewWaitUntilTerminated, Code);
    // config.toml holds the device secret and the server URL the SYSTEM service talks to:
    // SYSTEM + Administrators only, no inherited Users read (docs/Security.md §6). Re-applied on
    // upgrade, which also removes the Users:Modify grant older installers added. The tray elevates
    // (UAC) for pair and unpair, which need the secret.
    Exec(ExpandConstant('{sys}\icacls.exe'), '"' + DataDir() + '\config.toml" /inheritance:r /grant:r *S-1-5-18:F *S-1-5-32-544:F', '', SW_HIDE, ewWaitUntilTerminated, Code);
    InstallPawnIO();
    CreateService();
    if WizardIsTaskSelected('addtopath') then
      AddToPath(ExpandConstant('{app}'));
    // Silent installs skip the pairing page: start now; the agent waits until it is paired.
    if WizardSilent() then
      Sc('start {#ServiceName}');
  end;
end;

// --- Pairing page (installer/pairing-page.iss) ------------------------------------------------

#include "pairing-page.iss"

// Nexus theme, matching the app: navy frame and banner, yellow titles, cream content pages.
const
  ThemeNavy = $2E1B0F;    // #0F1B2E (Inno colours are $BBGGRR)
  ThemeCream = $E7F8FF;   // #FFF8E7
  ThemeYellow = $15CCFA;  // #FACC15
  ThemeInk = $23273E;     // #3E2723
  ThemeMuted = $E1D5CB;   // #CBD5E1

procedure ApplyNexusTheme();
begin
  WizardForm.Color := ThemeNavy;
  // Top banner on inner pages
  WizardForm.MainPanel.Color := ThemeNavy;
  WizardForm.PageNameLabel.Font.Color := ThemeYellow;
  WizardForm.PageNameLabel.Font.Style := [fsBold];
  WizardForm.PageDescriptionLabel.Font.Color := ThemeMuted;
  // Content area: cream card with dark ink text (system checkboxes stay readable on it)
  WizardForm.InnerPage.Color := ThemeCream;
  // Welcome and Finish pages sit next to the pixel-art image
  WizardForm.WelcomePage.Color := ThemeNavy;
  WizardForm.WelcomeLabel1.Font.Color := ThemeYellow;
  WizardForm.WelcomeLabel2.Font.Color := ThemeMuted;
  WizardForm.FinishedPage.Color := ThemeNavy;
  WizardForm.FinishedHeadingLabel.Font.Color := ThemeYellow;
  WizardForm.FinishedLabel.Font.Color := ThemeMuted;
  WizardForm.RunList.Color := ThemeNavy;
  WizardForm.RunList.Font.Color := ThemeMuted;
  WizardForm.WelcomeLabel1.Font.Size := 18;
  WizardForm.WelcomeLabel1.Font.Style := [fsBold];
  WizardForm.FinishedHeadingLabel.Font.Size := 18;
  WizardForm.Bevel.Visible := False;
  WizardForm.Bevel1.Visible := False;
end;

// --- First screen: one decision ----------------------------------------------------------
// Agree (checkbox) and Install. Location and options stay behind "Customize", so most people
// see Welcome -> Installing -> Pair your phone -> Done.
var
  AgreeBox: TNewCheckBox;
  Customize: Boolean;

procedure OpenTarget(Target: String);
var
  Code: Integer;
begin
  ShellExec('open', Target, '', '', SW_SHOWNORMAL, ewNoWait, Code);
end;

procedure LicenseClick(Sender: TObject);
begin
  ExtractTemporaryFile('LEGAL_AGREEMENT.rtf');
  OpenTarget(ExpandConstant('{tmp}\LEGAL_AGREEMENT.rtf'));
end;

procedure TermsClick(Sender: TObject);
begin
  OpenTarget('https://github.com/theallmyti/Nexus/blob/main/docs/TERMS.md');
end;

procedure PrivacyClick(Sender: TObject);
begin
  OpenTarget('https://github.com/theallmyti/Nexus/blob/main/docs/PRIVACY.md');
end;

procedure AgreeChanged(Sender: TObject);
begin
  WizardForm.NextButton.Enabled := AgreeBox.Checked;
end;

procedure AgreeLabelClick(Sender: TObject);
begin
  AgreeBox.Checked := not AgreeBox.Checked;
end;

procedure CustomizeClick(Sender: TObject);
begin
  if not AgreeBox.Checked then
  begin
    MsgBox('Please agree to the licence agreement, terms and privacy policy first.', mbInformation, MB_OK);
    Exit;
  end;
  Customize := True;
  WizardForm.NextButton.OnClick(WizardForm.NextButton);
end;

// A small text link in the theme's yellow; returns its right edge so links can sit in a row.
function AddLink(Caption: String; X, Y: Integer; Handler: TNotifyEvent): Integer;
var
  L: TNewStaticText;
begin
  L := TNewStaticText.Create(WizardForm);
  L.Parent := WizardForm.WelcomePage;
  L.Caption := Caption;
  L.Left := X;
  L.Top := Y;
  L.Cursor := crHand;
  L.Font.Color := ThemeYellow;
  L.Font.Style := [fsUnderline];
  L.OnClick := Handler;
  Result := L.Left + L.Width;
end;

procedure BuildWelcome();
var
  Lbl: TNewStaticText;
  Top, X: Integer;
begin
  WizardForm.WelcomeLabel2.AutoSize := True;
  Top := WizardForm.WelcomeLabel2.Top + WizardForm.WelcomeLabel2.Height + ScaleY(24);

  // Checkbox without caption (Windows draws checkbox text in black, unreadable on navy) + own label.
  AgreeBox := TNewCheckBox.Create(WizardForm);
  AgreeBox.Parent := WizardForm.WelcomePage;
  AgreeBox.Left := WizardForm.WelcomeLabel2.Left;
  AgreeBox.Top := Top + ScaleY(1);
  AgreeBox.Width := ScaleX(17);
  AgreeBox.Height := ScaleY(17);
  AgreeBox.OnClick := @AgreeChanged;

  Lbl := TNewStaticText.Create(WizardForm);
  Lbl.Parent := WizardForm.WelcomePage;
  Lbl.Left := AgreeBox.Left + ScaleX(24);
  Lbl.Top := Top;
  Lbl.Width := WizardForm.WelcomeLabel2.Width - ScaleX(24);
  Lbl.WordWrap := True;
  Lbl.Caption := 'I agree to the Nexus licence agreement, terms of service and privacy policy.';
  Lbl.Font.Color := ThemeMuted;
  Lbl.Cursor := crHand;
  Lbl.OnClick := @AgreeLabelClick;

  Top := Lbl.Top + Lbl.Height + ScaleY(6);
  X := AddLink('Licence agreement', Lbl.Left, Top, @LicenseClick) + ScaleX(14);
  X := AddLink('Terms', X, Top, @TermsClick) + ScaleX(14);
  AddLink('Privacy', X, Top, @PrivacyClick);

  // Bottom of the page: options for people who want them.
  AddLink('Customize location and options', WizardForm.WelcomeLabel2.Left,
    WizardForm.WelcomePage.Height - ScaleY(28), @CustomizeClick);
end;

function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := False;
  if (PageID = wpSelectDir) or (PageID = wpSelectTasks) then
    Result := not Customize;
end;

// --- Welcome / Finish animation ------------------------------------------------------------
// Only the patch with the man and the cat changes, so the frames are tiny (34x18 art pixels).
// They sit exactly over that patch of the side image and are scaled up without smoothing.
const
  AnimFrameCount = 8;
  ArtW = 41;      // art grid of the side image
  ArtH = 78.5;    // visible rows after cropping
  BoxX = 0; BoxY = 60; BoxW = 34; BoxH = 18;
var
  AnimWelcome, AnimFinish: TBitmapImage;
  AnimTimer: LongWord;
  AnimFrame: Integer;

function CreateAnim(Host: TBitmapImage): TBitmapImage;
begin
  Result := TBitmapImage.Create(WizardForm);
  Result.Parent := Host.Parent;
  Result.Stretch := True;
  Result.Left := Host.Left + Round(BoxX * Host.Width / ArtW);
  Result.Top := Host.Top + Round(BoxY * Host.Height / ArtH);
  Result.Width := Round(BoxW * Host.Width / ArtW);
  Result.Height := Host.Top + Round((BoxY + BoxH) * Host.Height / ArtH) - Result.Top;
  Result.BringToFront;
end;

procedure AnimTick(Wnd, Msg, IdEvent, Time: LongWord);
var
  Path: String;
begin
  AnimFrame := (AnimFrame + 1) mod AnimFrameCount;
  Path := ExpandConstant('{tmp}\anim-' + IntToStr(AnimFrame) + '.bmp');
  if (WizardForm.CurPageID = wpWelcome) then
    AnimWelcome.Bitmap.LoadFromFile(Path)
  else if (WizardForm.CurPageID = wpFinished) then
    AnimFinish.Bitmap.LoadFromFile(Path);
end;

procedure StartAnimation();
var
  I: Integer;
begin
  for I := 0 to AnimFrameCount - 1 do
    ExtractTemporaryFile('anim-' + IntToStr(I) + '.bmp');
  AnimWelcome := CreateAnim(WizardForm.WizardBitmapImage);
  AnimFinish := CreateAnim(WizardForm.WizardBitmapImage2);
  AnimWelcome.Bitmap.LoadFromFile(ExpandConstant('{tmp}\anim-0.bmp'));
  AnimFinish.Bitmap.LoadFromFile(ExpandConstant('{tmp}\anim-0.bmp'));
  AnimTimer := SetTimer(0, 0, 200, CreateCallback(@AnimTick));
end;

procedure InitializeWizard();
begin
  ApplyNexusTheme();
  BuildWelcome();
  StartAnimation();
  CreatePairingPage(wpInstalling);
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  // The step that starts installing says so.
  if CurPageID = wpWelcome then
  begin
    WizardForm.NextButton.Caption := 'Install';
    WizardForm.NextButton.Enabled := AgreeBox.Checked;
  end
  else if CurPageID = wpSelectTasks then
    WizardForm.NextButton.Caption := 'Install';
  if CurPageID = PairPage.ID then
    StartPairing(ExpandConstant('{app}\nexus.exe'), PairDir(), DataDir() + '\config.toml');
end;

// Next (paired) or Skip: start the service either way; an unpaired agent waits until paired.
function NextButtonClick(CurPageID: Integer): Boolean;
begin
  Result := True;
  if CurPageID = PairPage.ID then
  begin
    Result := FinishPairingPage();
    if Result then
      Sc('start {#ServiceName}');
  end;
end;

procedure DeinitializeSetup();
begin
  if AnimTimer <> 0 then
    KillTimer(0, AnimTimer);
  StopPairing();
end;

// --- Uninstall -------------------------------------------------------------------------------

// Runs an uninstall command line from the registry, e.g. "C:\...\uninstall.exe" -uninstall -silent.
procedure RunCommandLine(CommandLine: String);
var
  Exe, Params: String;
  P, Code: Integer;
begin
  CommandLine := Trim(CommandLine);
  if Copy(CommandLine, 1, 1) = '"' then
  begin
    Delete(CommandLine, 1, 1);
    P := Pos('"', CommandLine);
    Exe := Copy(CommandLine, 1, P - 1);
    Params := Trim(Copy(CommandLine, P + 1, Length(CommandLine)));
  end
  else
  begin
    P := Pos(' ', CommandLine);
    if P = 0 then
    begin
      Exe := CommandLine;
      Params := '';
    end
    else
    begin
      Exe := Copy(CommandLine, 1, P - 1);
      Params := Trim(Copy(CommandLine, P + 1, Length(CommandLine)));
    end;
  end;
  Exec(Exe, Params, '', SW_HIDE, ewWaitUntilTerminated, Code);
  Log('ran ' + Exe + ' ' + Params + ' -> ' + IntToStr(Code));
end;

procedure RemovePawnIO();
var
  Added: Cardinal;
  Quiet: String;
begin
  if not PawnIOInstalled() then
    Exit;
  Added := 0;
  RegQueryDWordValue(HKLM, '{#StateKey}', 'InstalledPawnIO', Added);
  // Added by another app: ask first, it may still need the driver.
  if (Added <> 1) and (UninstallSilent() or
     (MsgBox('Also remove the PawnIO driver? Other apps (for example Fan Control or ' +
       'LibreHardwareMonitor) may use it.', mbConfirmation, MB_YESNO) <> IDYES)) then
    Exit;
  if RegQueryStringValue(HKLM, 'SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\PawnIO',
       'QuietUninstallString', Quiet) then
    RunCommandLine(Quiet);
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  Code: Integer;
begin
  if CurUninstallStep = usUninstall then
  begin
    Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /IM nexus.exe', '', SW_HIDE, ewWaitUntilTerminated, Code);
    Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /IM pc-monitor-agent.exe', '', SW_HIDE, ewWaitUntilTerminated, Code);
    Sc('stop {#ServiceName}');
    Exec(ExpandConstant('{sys}\sc.exe'), 'stop PCMonitorAgent', '', SW_HIDE, ewWaitUntilTerminated, Code);
    // Remove this computer and its history from Nexus (needs the config, so before files go).
    if FileExists(ExpandConstant('{app}\nexus.exe')) then
      Exec(ExpandConstant('{app}\nexus.exe'), 'unpair "' + DataDir() + '\config.toml"',
        '', SW_HIDE, ewWaitUntilTerminated, Code)
    else if FileExists(ExpandConstant('{app}\pc-monitor-agent.exe')) then
      Exec(ExpandConstant('{app}\pc-monitor-agent.exe'), 'unpair "' + DataDir() + '\config.toml"',
        '', SW_HIDE, ewWaitUntilTerminated, Code);
    Log('unpair -> ' + IntToStr(Code));
    Sc('delete {#ServiceName}');
    Exec(ExpandConstant('{sys}\sc.exe'), 'delete PCMonitorAgent', '', SW_HIDE, ewWaitUntilTerminated, Code);
    RemoveFromPath(ExpandConstant('{app}'));
    RemovePawnIO();
    RegDeleteKeyIncludingSubkeys(HKLM, '{#StateKey}');
  end;
end;
