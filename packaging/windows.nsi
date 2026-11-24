; Windows installer of the desktop app (NSIS 3, Unicode). Adapted from Miko-Kagura packaging/windows.nsi (same author, see
; THIRD_PARTY_NOTICES.md) for the Compose Desktop app image (jpackage + jlink runtime).
;
; Per-user install, no elevation. The uninstaller removes ONLY what this installer wrote (the app image folders and files, the
; shortcuts, the uninstall entry, the link handlers and the app's autostart value) and never touches the data folder
; %APPDATA%\Nijiyome (history, extensions, accounts, logs).
;
;   makensis /DPAYLOAD=<app image dir with Nijiyome.exe> /DVERSION=0.1.0 /DOUTFILE=<setup.exe> /DLICENSE=<LICENSE> windows.nsi
;
; Built by packaging/windows/build-windows.ps1 (and the release workflow). Not run on the development machine (no Windows there).
!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "FileFunc.nsh"
!include "x64.nsh"

!ifndef PAYLOAD
  !error "Pass /DPAYLOAD=<folder with Nijiyome.exe, app\ and runtime\>"
!endif
!ifndef VERSION
  !define VERSION "0.1.0"
!endif
!ifndef OUTFILE
  !define OUTFILE "Nijiyome-v${VERSION}-windows-x64-setup.exe"
!endif
!ifndef LICENSE
  !define LICENSE "..\LICENSE"
!endif
; VIProductVersion needs four numbers: the version without a pre-release suffix, plus .0 (build-windows.ps1 passes it).
!ifndef WINDOWS_VERSION
  !define WINDOWS_VERSION "0.1.0.0"
!endif

Unicode true
Name "Nijiyome"
OutFile "${OUTFILE}"
InstallDir "$LOCALAPPDATA\Programs\Nijiyome"
InstallDirRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\app.nijiyome" "InstallLocation"
RequestExecutionLevel user
SetCompressor /SOLID lzma
VIProductVersion "${WINDOWS_VERSION}"
VIAddVersionKey "ProductName" "Nijiyome"
VIAddVersionKey "FileDescription" "Nijiyome Setup"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "ProductVersion" "${VERSION}"
VIAddVersionKey "LegalCopyright" "AGPL-3.0-or-later"

!define REGKEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\app.nijiyome"
!define CAPS "Software\Nijiyome\Capabilities"
!define EXE "$INSTDIR\Nijiyome.exe"
!define MUI_ICON "${PAYLOAD}\Nijiyome.ico"
!define MUI_UNICON "${PAYLOAD}\Nijiyome.ico"
!define MUI_ABORTWARNING
!insertmacro MUI_PAGE_LICENSE "${LICENSE}"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN "${EXE}"
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "Spanish"
LangString CloseApp ${LANG_ENGLISH} "Close Nijiyome before continuing."
LangString CloseApp ${LANG_SPANISH} "Cierra Nijiyome antes de continuar."

Function .onInit
    SetShellVarContext current
    ${IfNot} ${RunningX64}
        Abort "This package requires Windows x64."
    ${EndIf}
    SetRegView 64
    !insertmacro MUI_LANGDLL_DISPLAY
FunctionEnd

Function un.onInit
    SetShellVarContext current
    SetRegView 64
FunctionEnd

; A running app keeps its runtime DLLs open: opening one for writing fails while it runs.
!macro CheckAppClosedMacro PREFIX
Function ${PREFIX}CheckAppClosed
    IfFileExists "$INSTDIR\runtime\bin\server\jvm.dll" 0 done
    ClearErrors
    FileOpen $0 "$INSTDIR\runtime\bin\server\jvm.dll" a
    ${If} ${Errors}
        MessageBox MB_OK|MB_ICONEXCLAMATION "$(CloseApp)" /SD IDOK
        SetErrorLevel 1
        Abort
    ${EndIf}
    FileClose $0
    done:
FunctionEnd
!macroend
!insertmacro CheckAppClosedMacro ""
!insertmacro CheckAppClosedMacro "un."

Section "Install"
    Call CheckAppClosed
    ; An update replaces the app image whole (old jars must not stay next to the new ones).
    RMDir /r "$INSTDIR\app"
    RMDir /r "$INSTDIR\runtime"
    SetOutPath "$INSTDIR"
    File /r "${PAYLOAD}\*"
    WriteUninstaller "$INSTDIR\uninstall.exe"
    CreateDirectory "$SMPROGRAMS\Nijiyome"
    CreateShortcut "$SMPROGRAMS\Nijiyome\Nijiyome.lnk" "${EXE}"
    CreateShortcut "$DESKTOP\Nijiyome.lnk" "${EXE}"

    ; nijiyome:// links (OAuth on Android-style links, AniList profiles) open the app; the app forwards them to the running window.
    WriteRegStr HKCU "Software\Classes\nijiyome" "" "URL:Nijiyome"
    WriteRegStr HKCU "Software\Classes\nijiyome" "URL Protocol" ""
    WriteRegStr HKCU "Software\Classes\nijiyome\DefaultIcon" "" "${EXE},0"
    WriteRegStr HKCU "Software\Classes\nijiyome\shell\open\command" "" '"${EXE}" "%1"'
    ; magnet: and .torrent: offered as a choice (Settings › Default apps), never taken over from another torrent client.
    WriteRegStr HKCU "Software\Classes\Nijiyome.magnet" "" "Magnet link (Nijiyome)"
    WriteRegStr HKCU "Software\Classes\Nijiyome.magnet" "URL Protocol" ""
    WriteRegStr HKCU "Software\Classes\Nijiyome.magnet\DefaultIcon" "" "${EXE},0"
    WriteRegStr HKCU "Software\Classes\Nijiyome.magnet\shell\open\command" "" '"${EXE}" "%1"'
    WriteRegStr HKCU "Software\Classes\Nijiyome.torrent" "" "Torrent file (Nijiyome)"
    WriteRegStr HKCU "Software\Classes\Nijiyome.torrent\DefaultIcon" "" "${EXE},0"
    WriteRegStr HKCU "Software\Classes\Nijiyome.torrent\shell\open\command" "" '"${EXE}" "%1"'
    WriteRegStr HKCU "Software\Classes\.torrent\OpenWithProgids" "Nijiyome.torrent" ""
    WriteRegStr HKCU "${CAPS}" "ApplicationName" "Nijiyome"
    WriteRegStr HKCU "${CAPS}" "ApplicationDescription" "Anime player"
    WriteRegStr HKCU "${CAPS}\URLAssociations" "magnet" "Nijiyome.magnet"
    WriteRegStr HKCU "${CAPS}\URLAssociations" "nijiyome" "nijiyome"
    WriteRegStr HKCU "${CAPS}\FileAssociations" ".torrent" "Nijiyome.torrent"
    WriteRegStr HKCU "Software\RegisteredApplications" "Nijiyome" "${CAPS}"

    WriteRegStr HKCU "${REGKEY}" "DisplayName" "Nijiyome"
    WriteRegStr HKCU "${REGKEY}" "DisplayVersion" "${VERSION}"
    WriteRegStr HKCU "${REGKEY}" "Publisher" "Nijiyome"
    WriteRegStr HKCU "${REGKEY}" "InstallLocation" "$INSTDIR"
    WriteRegStr HKCU "${REGKEY}" "DisplayIcon" "${EXE}"
    WriteRegStr HKCU "${REGKEY}" "UninstallString" '$\"$INSTDIR\uninstall.exe$\"'
    WriteRegStr HKCU "${REGKEY}" "QuietUninstallString" '$\"$INSTDIR\uninstall.exe$\" /S'
    WriteRegDWORD HKCU "${REGKEY}" "NoModify" 1
    WriteRegDWORD HKCU "${REGKEY}" "NoRepair" 1
    ${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
    WriteRegDWORD HKCU "${REGKEY}" "EstimatedSize" $0
SectionEnd

; Explicit list: no recursive delete of $INSTDIR itself, so a user-chosen folder with other files survives.
Section "Uninstall"
    Call un.CheckAppClosed
    Delete "$INSTDIR\Nijiyome.exe"
    Delete "$INSTDIR\Nijiyome.ico"
    RMDir /r "$INSTDIR\app"
    RMDir /r "$INSTDIR\runtime"
    Delete "$INSTDIR\uninstall.exe"
    RMDir "$INSTDIR"
    Delete "$SMPROGRAMS\Nijiyome\Nijiyome.lnk"
    RMDir "$SMPROGRAMS\Nijiyome"
    Delete "$DESKTOP\Nijiyome.lnk"
    DeleteRegKey HKCU "Software\Classes\nijiyome"
    DeleteRegKey HKCU "Software\Classes\Nijiyome.magnet"
    DeleteRegKey HKCU "Software\Classes\Nijiyome.torrent"
    DeleteRegValue HKCU "Software\Classes\.torrent\OpenWithProgids" "Nijiyome.torrent"
    DeleteRegValue HKCU "Software\RegisteredApplications" "Nijiyome"
    DeleteRegKey HKCU "Software\Nijiyome"
    ; The app's own "Start with the system" entry (desktopApp Autostart.kt).
    DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "Nijiyome"
    DeleteRegKey HKCU "${REGKEY}"
SectionEnd
