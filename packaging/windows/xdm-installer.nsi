; ==============================================================================
; XDM Enhanced — NSIS Windows Installer Script
; Modern UI 2 installer for Windows x64 (perMachineOrUser)
; Package: XDM-Enhanced-Setup-v1.0.0-beta.3-win-x64.exe
; ==============================================================================

Unicode True
SetCompressor /SOLID lzma
RequestExecutionLevel user

; --- Product Definitions ---
!define PRODUCT_NAME "XDM Enhanced"
!ifndef PRODUCT_VERSION
  !define PRODUCT_VERSION "1.0.0-beta.3"
!endif
!define PRODUCT_VERSION_NUM "1.0.0.0"
!define PRODUCT_PUBLISHER "XDM Enhanced Contributors"
!define PRODUCT_WEB_SITE "https://github.com/jakepanlilio2000/XDM-Enhanced"
!define PRODUCT_SUPPORT_URL "https://github.com/jakepanlilio2000/XDM-Enhanced/issues"
!define PRODUCT_DIR_REGKEY "Software\XDM Enhanced"
!define PRODUCT_UNINST_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\XDM Enhanced"
!define APP_EXE "xdm-app.exe"

!ifndef APP_IMAGE_DIR
  !define APP_IMAGE_DIR "..\..\build\dist\windows-x64\xdm-app"
!endif

!ifndef OUT_FILE
  !define OUT_FILE "..\..\build\dist\windows-x64\XDM-Enhanced-Setup-v1.0.0-beta.3-win-x64.exe"
!endif

!ifndef ICON_FILE
  !define ICON_FILE "..\icons\xdm.ico"
!endif

!ifndef LICENSE_FILE
  !define LICENSE_FILE "gpl-3.0-notice.rtf"
!endif

Name "${PRODUCT_NAME}"
OutFile "${OUT_FILE}"

; --- Includes ---
!include "MUI2.nsh"
!include "x64.nsh"
!include "LogicLib.nsh"
!include "WinVer.nsh"
!include "FileFunc.nsh"

; --- Global Variables ---
Var IsAdmin
Var RegRoot
Var CustomDir

; --- Modern UI Configuration ---
!define MUI_ABORTWARNING
!define MUI_ICON "${ICON_FILE}"
!define MUI_UNICON "${ICON_FILE}"

; Welcome Page
!define MUI_WELCOMEPAGE_TITLE "Welcome to ${PRODUCT_NAME} Setup"
!define MUI_WELCOMEPAGE_TEXT "Setup will guide you through the installation of ${PRODUCT_NAME} v${PRODUCT_VERSION}.$\r$\n$\r$\n${PRODUCT_NAME} is a fast, powerful, and free open-source download manager with complete BitTorrent, browser integration, and high-DPI desktop support.$\r$\n$\r$\nClick Next to continue."
!insertmacro MUI_PAGE_WELCOME

; License Page (Open-Source GPL-3.0 with Rights Notice)
!define MUI_LICENSEPAGE_TEXT_TOP "Please review the license terms before proceeding. ${PRODUCT_NAME} is free and open-source software."
!define MUI_LICENSEPAGE_TEXT_BOTTOM "If you accept the terms of the agreement, click I Agree to continue. You must accept the agreement to install ${PRODUCT_NAME}."
!insertmacro MUI_PAGE_LICENSE "${LICENSE_FILE}"

; Components Page
!define MUI_COMPONENTSPAGE_NODESC
!insertmacro MUI_PAGE_COMPONENTS

; Directory Page
!insertmacro MUI_PAGE_DIRECTORY

; Installation Page
!insertmacro MUI_PAGE_INSTFILES

; Finish Page
!define MUI_FINISHPAGE_RUN "$INSTDIR\${APP_EXE}"
!define MUI_FINISHPAGE_RUN_TEXT "Launch ${PRODUCT_NAME}"
!insertmacro MUI_PAGE_FINISH

; Uninstaller Pages
!define MUI_UNWELCOMEFINISHPAGE_BITMAP_NOSTRETCH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH

; Language
!insertmacro MUI_LANGUAGE "English"

; --- Version Information ---
VIProductVersion "${PRODUCT_VERSION_NUM}"
VIAddVersionKey "ProductName" "${PRODUCT_NAME}"
VIAddVersionKey "ProductVersion" "${PRODUCT_VERSION}"
VIAddVersionKey "CompanyName" "${PRODUCT_PUBLISHER}"
VIAddVersionKey "FileDescription" "${PRODUCT_NAME} Setup"
VIAddVersionKey "FileVersion" "${PRODUCT_VERSION_NUM}"
VIAddVersionKey "LegalCopyright" "Copyright (C) 2026 XDM Enhanced Contributors"
VIAddVersionKey "OriginalFilename" "XDM-Enhanced-Setup-v1.0.0-beta.3-win-x64.exe"

; ==============================================================================
; Helper Function: Check & Prompt If App Is Running
; ==============================================================================
Function CheckAppRunning
  IfSilent SilentClose
  CheckLoop:
    nsExec::ExecToStack 'cmd /c "tasklist /FI \"IMAGENAME eq ${APP_EXE}\" | findstr /i /c:\"${APP_EXE}\""'
    Pop $0 ; exit code (0 if found, 1 if not)
    Pop $1 ; stdout
    ${If} $0 == 0
      MessageBox MB_RETRYCANCEL|MB_ICONEXCLAMATION \
        "${PRODUCT_NAME} is currently running.$\r$\n$\r$\nPlease close ${PRODUCT_NAME} before continuing." \
        IDRETRY CheckLoop IDCANCEL Cancel
      Cancel:
        Abort
    ${EndIf}
    Goto DoneCheck
  SilentClose:
    nsExec::Exec 'taskkill /im ${APP_EXE}'
    Sleep 1000
  DoneCheck:
FunctionEnd

Function un.CheckAppRunning
  IfSilent UnSilentClose
  UnCheckLoop:
    nsExec::ExecToStack 'cmd /c "tasklist /FI \"IMAGENAME eq ${APP_EXE}\" | findstr /i /c:\"${APP_EXE}\""'
    Pop $0
    Pop $1
    ${If} $0 == 0
      MessageBox MB_RETRYCANCEL|MB_ICONEXCLAMATION \
        "${PRODUCT_NAME} is currently running.$\r$\n$\r$\nPlease close ${PRODUCT_NAME} before continuing with uninstallation." \
        IDRETRY UnCheckLoop IDCANCEL UnCancel
      UnCancel:
        Abort
    ${EndIf}
    Goto UnDoneCheck
  UnSilentClose:
    nsExec::Exec 'taskkill /im ${APP_EXE}'
    Sleep 1000
  UnDoneCheck:
FunctionEnd

; ==============================================================================
; Initialization (.onInit)
; ==============================================================================
Function .onInit
  ; Ensure 64-bit Windows
  ${IfNot} ${RunningX64}
    MessageBox MB_OK|MB_ICONSTOP "${PRODUCT_NAME} requires a 64-bit version of Windows."
    Abort
  ${EndIf}

  ; Check running process
  Call CheckAppRunning

  ; Determine privilege level & default install location
  StrCpy $CustomDir "0"
  ${If} $INSTDIR != "$PROGRAMFILES64\XDM Enhanced"
  ${AndIf} $INSTDIR != ""
    StrCpy $CustomDir "1"
  ${EndIf}

  UserInfo::GetAccountType
  Pop $0
  ${If} $0 == "Admin"
  ${OrIf} $0 == "Power"
    StrCpy $IsAdmin "1"
    StrCpy $RegRoot "HKLM"
    SetShellVarContext all
    ${If} $CustomDir == "0"
      StrCpy $INSTDIR "$PROGRAMFILES64\XDM Enhanced"
    ${EndIf}
  ${Else}
    StrCpy $IsAdmin "0"
    StrCpy $RegRoot "HKCU"
    SetShellVarContext current
    ${If} $CustomDir == "0"
      StrCpy $INSTDIR "$LOCALAPPDATA\Programs\XDM Enhanced"
    ${EndIf}
  ${EndIf}

  ; Upgrade check: if previously installed, reuse that location
  ReadRegStr $1 HKLM "${PRODUCT_DIR_REGKEY}" "InstallDir"
  ${If} $1 != ""
    StrCpy $INSTDIR $1
  ${Else}
    ReadRegStr $1 HKCU "${PRODUCT_DIR_REGKEY}" "InstallDir"
    ${If} $1 != ""
      StrCpy $INSTDIR $1
    ${EndIf}
  ${EndIf}
FunctionEnd

; ==============================================================================
; Uninstaller Initialization (un.onInit)
; ==============================================================================
Function un.onInit
  Call un.CheckAppRunning

  UserInfo::GetAccountType
  Pop $0
  ${If} $0 == "Admin"
  ${OrIf} $0 == "Power"
    SetShellVarContext all
  ${Else}
    SetShellVarContext current
  ${EndIf}
FunctionEnd

; ==============================================================================
; Component Sections
; ==============================================================================

; Section 1: Main Application (Required)
Section "${PRODUCT_NAME} Core Files (Required)" SEC_CORE
  SectionIn RO

  ; Prevent accidental overwriting of upstream official XDM (xdman)
  ${If} ${FileExists} "$INSTDIR\xdman.jar"
  ${OrIf} ${FileExists} "$INSTDIR\xdman.exe"
    MessageBox MB_OK|MB_ICONSTOP "The selected destination contains an existing official XDM (xdman) installation.$\r$\nTo protect your upstream installation, please select a distinct folder for ${PRODUCT_NAME}."
    Abort
  ${EndIf}

  SetOutPath "$INSTDIR"
  File /r "${APP_IMAGE_DIR}\*.*"

  ; Tune GC based on Windows build (Windows 10 build 17134+ supports ZGC)
  ReadRegStr $0 HKLM "SOFTWARE\Microsoft\Windows NT\CurrentVersion" "CurrentBuildNumber"
  ${If} $0 >= 17134
    ${If} ${FileExists} "$INSTDIR\app\xdm-app.cfg.zgc"
      CopyFiles /SILENT "$INSTDIR\app\xdm-app.cfg.zgc" "$INSTDIR\app\xdm-app.cfg"
    ${EndIf}
  ${EndIf}

  ; Create Uninstaller
  WriteUninstaller "$INSTDIR\uninstall.exe"

  ; Registry: App Settings & Identification
  ${If} $IsAdmin == "1"
    WriteRegStr HKLM "${PRODUCT_DIR_REGKEY}" "InstallDir" "$INSTDIR"
    WriteRegStr HKLM "${PRODUCT_DIR_REGKEY}" "Version" "${PRODUCT_VERSION}"
    WriteRegDWORD HKLM "${PRODUCT_DIR_REGKEY}" "Installed" 1

    WriteRegStr HKLM "${PRODUCT_UNINST_KEY}" "DisplayName" "${PRODUCT_NAME}"
    WriteRegStr HKLM "${PRODUCT_UNINST_KEY}" "DisplayVersion" "${PRODUCT_VERSION}"
    WriteRegStr HKLM "${PRODUCT_UNINST_KEY}" "Publisher" "${PRODUCT_PUBLISHER}"
    WriteRegStr HKLM "${PRODUCT_UNINST_KEY}" "URLInfoAbout" "${PRODUCT_WEB_SITE}"
    WriteRegStr HKLM "${PRODUCT_UNINST_KEY}" "HelpLink" "${PRODUCT_SUPPORT_URL}"
    WriteRegStr HKLM "${PRODUCT_UNINST_KEY}" "DisplayIcon" "$INSTDIR\${APP_EXE},0"
    WriteRegStr HKLM "${PRODUCT_UNINST_KEY}" "UninstallString" '"$INSTDIR\uninstall.exe"'
    WriteRegStr HKLM "${PRODUCT_UNINST_KEY}" "QuietUninstallString" '"$INSTDIR\uninstall.exe" /S'
    WriteRegStr HKLM "${PRODUCT_UNINST_KEY}" "InstallLocation" "$INSTDIR"
    WriteRegDWORD HKLM "${PRODUCT_UNINST_KEY}" "NoModify" 1
    WriteRegDWORD HKLM "${PRODUCT_UNINST_KEY}" "NoRepair" 1

    ${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
    IntFmt $0 "0x%08X" $0
    WriteRegDWORD HKLM "${PRODUCT_UNINST_KEY}" "EstimatedSize" "$0"
  ${Else}
    WriteRegStr HKCU "${PRODUCT_DIR_REGKEY}" "InstallDir" "$INSTDIR"
    WriteRegStr HKCU "${PRODUCT_DIR_REGKEY}" "Version" "${PRODUCT_VERSION}"
    WriteRegDWORD HKCU "${PRODUCT_DIR_REGKEY}" "Installed" 1

    WriteRegStr HKCU "${PRODUCT_UNINST_KEY}" "DisplayName" "${PRODUCT_NAME}"
    WriteRegStr HKCU "${PRODUCT_UNINST_KEY}" "DisplayVersion" "${PRODUCT_VERSION}"
    WriteRegStr HKCU "${PRODUCT_UNINST_KEY}" "Publisher" "${PRODUCT_PUBLISHER}"
    WriteRegStr HKCU "${PRODUCT_UNINST_KEY}" "URLInfoAbout" "${PRODUCT_WEB_SITE}"
    WriteRegStr HKCU "${PRODUCT_UNINST_KEY}" "HelpLink" "${PRODUCT_SUPPORT_URL}"
    WriteRegStr HKCU "${PRODUCT_UNINST_KEY}" "DisplayIcon" "$INSTDIR\${APP_EXE},0"
    WriteRegStr HKCU "${PRODUCT_UNINST_KEY}" "UninstallString" '"$INSTDIR\uninstall.exe"'
    WriteRegStr HKCU "${PRODUCT_UNINST_KEY}" "QuietUninstallString" '"$INSTDIR\uninstall.exe" /S'
    WriteRegStr HKCU "${PRODUCT_UNINST_KEY}" "InstallLocation" "$INSTDIR"
    WriteRegDWORD HKCU "${PRODUCT_UNINST_KEY}" "NoModify" 1
    WriteRegDWORD HKCU "${PRODUCT_UNINST_KEY}" "NoRepair" 1

    ${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
    IntFmt $0 "0x%08X" $0
    WriteRegDWORD HKCU "${PRODUCT_UNINST_KEY}" "EstimatedSize" "$0"
  ${EndIf}
SectionEnd

; Section 2: Start Menu Shortcut
Section "Start Menu Shortcut" SEC_STARTMENU
  CreateDirectory "$SMPROGRAMS\${PRODUCT_NAME}"
  CreateShortcut "$SMPROGRAMS\${PRODUCT_NAME}\${PRODUCT_NAME}.lnk" "$INSTDIR\${APP_EXE}" "" "$INSTDIR\${APP_EXE}" 0
  CreateShortcut "$SMPROGRAMS\${PRODUCT_NAME}\Uninstall ${PRODUCT_NAME}.lnk" "$INSTDIR\uninstall.exe" "" "$INSTDIR\uninstall.exe" 0
SectionEnd

; Section 3: Desktop Shortcut
Section "Desktop Shortcut" SEC_DESKTOP
  CreateShortcut "$DESKTOP\${PRODUCT_NAME}.lnk" "$INSTDIR\${APP_EXE}" "" "$INSTDIR\${APP_EXE}" 0
SectionEnd

; Section 4: Start with Windows
Section /o "Start with Windows" SEC_AUTOSTART
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "${PRODUCT_NAME}" '"$INSTDIR\${APP_EXE}" --minimized'
SectionEnd

; Section 5: Protocol and File Associations
Section "Protocol and File Associations" SEC_ASSOCIATIONS
  ${If} $IsAdmin == "1"
    ; Machine-wide associations
    WriteRegStr HKCR "xdm-app" "" "URL:XDM Enhanced Protocol"
    WriteRegStr HKCR "xdm-app" "URL Protocol" ""
    WriteRegStr HKCR "xdm-app\DefaultIcon" "" "$INSTDIR\${APP_EXE},0"
    WriteRegStr HKCR "xdm-app\shell\open\command" "" '"$INSTDIR\${APP_EXE}" "%1"'

    WriteRegStr HKCR ".torrent" "" "XDM.Enhanced.Torrent"
    WriteRegStr HKCR "XDM.Enhanced.Torrent" "" "BitTorrent Document"
    WriteRegStr HKCR "XDM.Enhanced.Torrent\DefaultIcon" "" "$INSTDIR\${APP_EXE},0"
    WriteRegStr HKCR "XDM.Enhanced.Torrent\shell\open\command" "" '"$INSTDIR\${APP_EXE}" "%1"'

    WriteRegStr HKCR "magnet" "" "URL:Magnet Protocol"
    WriteRegStr HKCR "magnet" "URL Protocol" ""
    WriteRegStr HKCR "magnet\DefaultIcon" "" "$INSTDIR\${APP_EXE},0"
    WriteRegStr HKCR "magnet\shell\open\command" "" '"$INSTDIR\${APP_EXE}" "%1"'
  ${Else}
    ; Per-user associations
    WriteRegStr HKCU "Software\Classes\xdm-app" "" "URL:XDM Enhanced Protocol"
    WriteRegStr HKCU "Software\Classes\xdm-app" "URL Protocol" ""
    WriteRegStr HKCU "Software\Classes\xdm-app\DefaultIcon" "" "$INSTDIR\${APP_EXE},0"
    WriteRegStr HKCU "Software\Classes\xdm-app\shell\open\command" "" '"$INSTDIR\${APP_EXE}" "%1"'

    WriteRegStr HKCU "Software\Classes\.torrent" "" "XDM.Enhanced.Torrent"
    WriteRegStr HKCU "Software\Classes\XDM.Enhanced.Torrent" "" "BitTorrent Document"
    WriteRegStr HKCU "Software\Classes\XDM.Enhanced.Torrent\DefaultIcon" "" "$INSTDIR\${APP_EXE},0"
    WriteRegStr HKCU "Software\Classes\XDM.Enhanced.Torrent\shell\open\command" "" '"$INSTDIR\${APP_EXE}" "%1"'

    WriteRegStr HKCU "Software\Classes\magnet" "" "URL:Magnet Protocol"
    WriteRegStr HKCU "Software\Classes\magnet" "URL Protocol" ""
    WriteRegStr HKCU "Software\Classes\magnet\DefaultIcon" "" "$INSTDIR\${APP_EXE},0"
    WriteRegStr HKCU "Software\Classes\magnet\shell\open\command" "" '"$INSTDIR\${APP_EXE}" "%1"'
  ${EndIf}
SectionEnd

; ==============================================================================
; Section Descriptions (Tooltips)
; ==============================================================================
!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
  !insertmacro MUI_DESCRIPTION_TEXT ${SEC_CORE} "Installs ${PRODUCT_NAME} application binaries, libraries, and optimized Liberica JDK 25 runtime."
  !insertmacro MUI_DESCRIPTION_TEXT ${SEC_STARTMENU} "Creates shortcuts in the Windows Start Menu."
  !insertmacro MUI_DESCRIPTION_TEXT ${SEC_DESKTOP} "Places an ${PRODUCT_NAME} shortcut on your desktop."
  !insertmacro MUI_DESCRIPTION_TEXT ${SEC_AUTOSTART} "Automatically launches ${PRODUCT_NAME} in the system tray when you log in."
  !insertmacro MUI_DESCRIPTION_TEXT ${SEC_ASSOCIATIONS} "Associates ${PRODUCT_NAME} with xdm-app:// browser integration, magnet links, and .torrent files."
!insertmacro MUI_FUNCTION_DESCRIPTION_END

; ==============================================================================
; Uninstallation Section
; ==============================================================================
Section "Uninstall"
  ; Remove shortcuts
  Delete "$SMPROGRAMS\${PRODUCT_NAME}\${PRODUCT_NAME}.lnk"
  Delete "$SMPROGRAMS\${PRODUCT_NAME}\Uninstall ${PRODUCT_NAME}.lnk"
  RMDir "$SMPROGRAMS\${PRODUCT_NAME}"
  Delete "$DESKTOP\${PRODUCT_NAME}.lnk"

  ; Remove autostart registration
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "${PRODUCT_NAME}"

  ; Remove protocol and file associations
  DeleteRegKey HKCR "xdm-app"
  DeleteRegKey HKCU "Software\Classes\xdm-app"

  ReadRegStr $0 HKCR ".torrent" ""
  ${If} $0 == "XDM.Enhanced.Torrent"
    DeleteRegKey HKCR ".torrent"
  ${EndIf}
  ReadRegStr $0 HKCU "Software\Classes\.torrent" ""
  ${If} $0 == "XDM.Enhanced.Torrent"
    DeleteRegKey HKCU "Software\Classes\.torrent"
  ${EndIf}
  DeleteRegKey HKCR "XDM.Enhanced.Torrent"
  DeleteRegKey HKCU "Software\Classes\XDM.Enhanced.Torrent"

  ReadRegStr $0 HKCR "magnet\shell\open\command" ""
  ${If} $0 == '"$INSTDIR\${APP_EXE}" "%1"'
    DeleteRegKey HKCR "magnet"
  ${EndIf}
  ReadRegStr $0 HKCU "Software\Classes\magnet\shell\open\command" ""
  ${If} $0 == '"$INSTDIR\${APP_EXE}" "%1"'
    DeleteRegKey HKCU "Software\Classes\magnet"
  ${EndIf}

  ; Remove ARP and App registry keys
  DeleteRegKey HKLM "${PRODUCT_UNINST_KEY}"
  DeleteRegKey HKCU "${PRODUCT_UNINST_KEY}"
  DeleteRegKey HKLM "${PRODUCT_DIR_REGKEY}"
  DeleteRegKey HKCU "${PRODUCT_DIR_REGKEY}"

  ; Prompt user regarding personal preferences and download history
  ; Note: User downloaded files are NEVER touched!
  IfSilent SkipDataPrompt
  MessageBox MB_YESNO|MB_ICONQUESTION \
    "Do you want to delete your personal settings and download history?$\r$\n$\r$\nNote: Your downloaded files will NEVER be deleted." \
    IDNO SkipDataPrompt
      RMDir /r "$APPDATA\xdm-app"
      RMDir /r "$PROFILE\.xdm"
  SkipDataPrompt:

  ; Delete application files
  RMDir /r "$INSTDIR\app"
  RMDir /r "$INSTDIR\runtime"
  Delete "$INSTDIR\${APP_EXE}"
  Delete "$INSTDIR\uninstall.exe"
  RMDir "$INSTDIR"
SectionEnd
