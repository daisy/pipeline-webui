# This installs two files, app.exe and logo.ico, creates a start menu shortcut, builds an uninstaller, and
# adds uninstall information to the registry for Add/Remove Programs
 
# To get started, put this script into a folder with the two files (app.exe, logo.ico, and license.rtf -
# You'll have to create these yourself) and run makensis on it
 
# If you change the names "app.exe", "logo.ico", or "license.rtf" you should do a search and replace - they
# show up in a few places.
# All the other settings can be tweaked by editing the !defines at the top of this script
!define APPNAME "DAISY Pipeline 2"
!define VERSION "1.6-BETA"
!define COMPANYNAME "DAISY Consortium"
!define DESCRIPTION "Diasy Pipeline 2 windows distribution"
!define PRODUCT_WEB_SITE "http://www.daisy.org/"
!define PRODUCT_REG_ROOT SHCTX
!define PRODUCT_REG_KEY "SOFTWARE\${APPNAME}"
!define PRODUCT_REG_VALUENAME_INSTDIR "Path"
!define PRODUCT_REG_VALUENAME_STARTMENU "StartMenuGroup"
!define PRODUCT_REG_KEY_UNINST "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}"
!define UNINSTALLER_NAME "Uninstall ${APPNAME}"
!define REQUIRED_JAVA_VER "1.6"
 
RequestExecutionLevel admin ;Require admin rights on NT6+ (When UAC is turned on)
 
;----------------------------------------------------------
;   Installer General Settings
;----------------------------------------------------------
Name "${APPNAME}"
;OutFile "${PROJECT_BUILD_DIR}\pipeline2-installer.exe"
ShowInstDetails show
ShowUnInstDetails show
SetCompressor zlib
InstallDir "$PROGRAMFILES\${APPNAME}"
;----------------------------------------------------------
; Maven properties
;----------------------------------------------------------
!include ../target/project.nsh

;----------------------------------------------------------
;   Multi-User settings
;----------------------------------------------------------
!define MULTIUSER_EXECUTIONLEVEL Highest
!define MULTIUSER_INSTALLMODE_INSTDIR "${APPNAME}"
!define MULTIUSER_INSTALLMODE_INSTDIR_REGISTRY_KEY "${PRODUCT_REG_KEY}"
!define MULTIUSER_INSTALLMODE_INSTDIR_REGISTRY_VALUENAME "${PRODUCT_REG_VALUENAME_INSTDIR}"
!include MultiUser.nsh

;----------------------------------------------------------
;   MUI Settings
;----------------------------------------------------------
; --- Includes Modern UI 2 ---
!include "MUI2.nsh"
!define MUI_ABORTWARNING
!define MUI_ICON "${NSISDIR}\Contrib\Graphics\Icons\modern-install.ico"
!define MUI_UNICON "${NSISDIR}\Contrib\Graphics\Icons\modern-uninstall.ico"
; --- Registry storage of selected language ---
!define MUI_LANGDLL_ALWAYSSHOW
!define MUI_LANGDLL_REGISTRY_ROOT ${PRODUCT_REG_ROOT}
!define MUI_LANGDLL_REGISTRY_KEY "${PRODUCT_REG_KEY}"
!define MUI_LANGDLL_REGISTRY_VALUENAME "NSIS:Language"
; ---- StartMenu Page Configuration ---
var SMGROUP
!define MUI_STARTMENUPAGE_DEFAULTFOLDER "${APPNAME}"
!define MUI_STARTMENUPAGE_REGISTRY_ROOT ${PRODUCT_REG_ROOT}
!define MUI_STARTMENUPAGE_REGISTRY_KEY "${PRODUCT_REG_KEY}"
!define MUI_STARTMENUPAGE_REGISTRY_VALUENAME "${PRODUCT_REG_VALUENAME_STARTMENU}"

;----------------------------------------------------------
;  Environ variables defines 
;----------------------------------------------------------

!define env_hklm 'HKLM "SYSTEM\CurrentControlSet\Control\Session Manager\Environment"'
!define env_hkcu 'HKCU "Environment"'



;----------------------------------------------------------
;   Headers and Macros
;----------------------------------------------------------
; required for JRE check:
!include WordFunc.nsh
!insertmacro VersionConvert
!insertmacro VersionCompare
;other
!include LogicLib.nsh
!include "Sections.nsh"
!include "winmessages.nsh"


;----------------------------------------------------------
;   Installer Pages
;----------------------------------------------------------
; ---- Installer Pages ----
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "license.txt"
!define MUI_PAGE_CUSTOMFUNCTION_SHOW CheckInstDirReg
!insertmacro MUI_PAGE_DIRECTORY
!define MUI_PAGE_CUSTOMFUNCTION_SHOW CheckSMDirReg
!insertmacro MUI_PAGE_STARTMENU Application $SMGROUP
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_LANGUAGE "English"
; ---- Uninstaller Pages ----
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
 
Function CheckInstDirReg
  ; Disable the directory chooser if it's an upgrade
  ReadRegStr $R0 ${PRODUCT_REG_ROOT} "${PRODUCT_REG_KEY}" "${PRODUCT_REG_VALUENAME_INSTDIR}"
  StrCmp $R0 "" donothing
    FindWindow $R0 "#32770" "" $HWNDPARENT
    GetDlgItem $R1 $R0 1019
    EnableWindow $R1 0
    GetDlgItem $R1 $R0 1001
    EnableWindow $R1 0
    GetDlgItem $R0 $HWNDPARENT 1
    System::Call "user32::SetFocus(i R0)"
  donothing:
FunctionEnd

Function CheckSMDirReg
  ; Disable the start menu chooser if it's an upgrade
  ReadRegStr $R0 ${PRODUCT_REG_ROOT} "${PRODUCT_REG_KEY}" "${PRODUCT_REG_VALUENAME_STARTMENU}"
  StrCmp $R0 "" donothing
    FindWindow $R0 "#32770" "" $HWNDPARENT
    GetDlgItem $R1 $R0 1002
    EnableWindow $R1 0
    GetDlgItem $R1 $R0 1004
    EnableWindow $R1 0
    GetDlgItem $R1 $R0 1005
    EnableWindow $R1 0
    GetDlgItem $R0 $HWNDPARENT 1
    System::Call "user32::SetFocus(i R0)"
  donothing:
FunctionEnd


;----------------------------------------------------------
;  Admin check 
;----------------------------------------------------------
!macro VerifyUserIsAdmin
UserInfo::GetAccountType
pop $0
${If} $0 != "admin" ;Require admin rights on NT4+
        messageBox mb_iconstop "Administrator rights required!"
        setErrorLevel 740 ;ERROR_ELEVATION_REQUIRED
        quit
${EndIf}
!macroend

###########################################################
###               Installer Sections                    ###
###########################################################

InstType "Default"
InstType /COMPONENTSONLYONCUSTOM
;----------------------------------------------------------
;   Initialization Callback
;----------------------------------------------------------

 
 
function .onInit
	setShellVarContext all
	!insertmacro VerifyUserIsAdmin
	; check the user priviledges
	!insertmacro MULTIUSER_INIT
functionEnd
 
;----------------------------------------------------------
;   JRE Check
;----------------------------------------------------------

Section -JRECheck SEC00-1

  var /GLOBAL JAVA_VER
  var /GLOBAL JAVA_HOME

  DetailPrint "Checking JRE version..."
  ReadRegStr $JAVA_VER HKLM "SOFTWARE\JavaSoft\Java Runtime Environment" CurrentVersion
  StrCmp "" "$JAVA_VER" JavaNotPresent CheckJavaVersion
  
  CheckJavaVersion:
    ;First check version number
    ${VersionConvert} $JAVA_VER "" $R1
    ${VersionCompare} $R1 ${REQUIRED_JAVA_VER} $R2
    IntCmp 2 $R2 JavaTooOld
    ;Then check binary file exist
    ReadRegStr $JAVA_HOME HKLM "SOFTWARE\JavaSoft\Java Runtime Environment\$JAVA_VER" JavaHome
    IfFileExists "$JAVA_HOME\bin\java.exe" 0 JavaNotPresent
    DetailPrint "Found a compatible JVM ($JAVA_VER)"
    ;Set JAVA_HOME env var
    ; HKLM (all users) vs HKCU (current user) defines
    WriteRegExpandStr ${env_hklm} JAVA_HOME "$JAVA_HOME" 
    ; make sure windows knows about the change
    SendMessage ${HWND_BROADCAST} ${WM_WININICHANGE} 0 "STR:Environment" /TIMEOUT=5000

    DetailPrint "JAVA_HOME set to $JAVA_HOME\bin"
    Goto End
  
  JavaTooOld:
        messageBox mb_iconstop "Java version too old. Please install Java JRE ${REQUIRED_JAVA_VER} or greater"
        setErrorLevel 740 ;ERROR_ELEVATION_REQUIRED
        quit
  
  JavaNotPresent:
        messageBox mb_iconstop "Java JRE not Found. Please install Java JRE ${REQUIRED_JAVA_VER} or greater"
        setErrorLevel 740 ;ERROR_ELEVATION_REQUIRED
        quit
  
  End:
SectionEnd
section -Main SEC01 
	setOutPath $INSTDIR
	SetOverwrite on
	file ./logo.ico 
	writeUninstaller "$INSTDIR\uninstall.exe"
	#Copy the whole daisy-pipeline dir
	setOutPath "$INSTDIR\${PROJECT_ARTIFACT_ID}"
 
	file /r "${PROJECT_BUILD_DIR}\${PROJECT_ARTIFACT_ID}-${VERSION}-desktop\daisy-pipeline" 
	file "${PROJECT_BUILD_DIR}\${PROJECT_ARTIFACT_ID}-${VERSION}-desktop\application.conf"
	# Start Menu
	createDirectory "$SMPROGRAMS\${COMPANYNAME}"
	createShortCut "$SMPROGRAMS\${COMPANYNAME}\${APPNAME}.lnk" "$INSTDIR\${PROJECT_ARTIFACT_ID}\daisy-pipeline\webui\start.bat" "" "$INSTDIR\logo.ico"
	CreateShortCut "$SMPROGRAMS\$SMGROUP\${COMPANYNAME}\unistall.lnk" "$INSTDIR\unistall.exe"

	############### 
	# Registry information for add/remove programs
	############### 

	WriteRegStr HKLM "${PRODUCT_REG_KEY_UNINST}${COMPANYNAME} ${APPNAME}" "DisplayName" "${COMPANYNAME} - ${APPNAME} - ${DESCRIPTION}"
	WriteRegStr HKLM "${PRODUCT_REG_KEY_UNINST}${COMPANYNAME} ${APPNAME}" "UninstallString" "$\"$INSTDIR\uninstall.exe$\""
	WriteRegStr HKLM "${PRODUCT_REG_KEY_UNINST}${COMPANYNAME} ${APPNAME}" "QuietUninstallString" "$\"$INSTDIR\uninstall.exe$\" /S"
	WriteRegStr HKLM "${PRODUCT_REG_KEY_UNINST}${COMPANYNAME} ${APPNAME}" "InstallLocation" "$\"$INSTDIR$\""
	WriteRegStr HKLM "${PRODUCT_REG_KEY_UNINST}${COMPANYNAME} ${APPNAME}" "DisplayIcon" "$\"$INSTDIR\logo.ico$\""
	WriteRegStr HKLM "${PRODUCT_REG_KEY_UNINST}${COMPANYNAME} ${APPNAME}" "Publisher" "$\"${COMPANYNAME}$\""
	WriteRegStr HKLM "${PRODUCT_REG_KEY_UNINST}${COMPANYNAME} ${APPNAME}" "DisplayVersion" "$\"${VERSION}$\""
	# There is no option for modifying or repairing the install
	WriteRegDWORD HKLM "${PRODUCT_REG_KEY_UNINST}${COMPANYNAME} ${APPNAME}" "NoModify" 1
	WriteRegDWORD HKLM "${PRODUCT_REG_KEY_UNINST}${COMPANYNAME} ${APPNAME}" "NoRepair" 1
	#pipelinehome registry entry
	WriteRegStr HKLM "${PRODUCT_REG_KEY}" "Pipeline2Home" "$\"$INSTDIR\${PROJECT_ARTIFACT_ID}\daisy-pipeline$\""

	; make sure windows knows about the change
	SendMessage ${HWND_BROADCAST} ${WM_WININICHANGE} 0 "STR:Environment" /TIMEOUT=5000
sectionEnd

;----------------------------------------------------------

# Uninstaller
 
function un.onInit
	SetShellVarContext all
 
	!insertmacro VerifyUserIsAdmin
functionEnd
 
section "uninstall"
 
	# Remove Start Menu launcher
	delete "$SMPROGRAMS\${COMPANYNAME}\${APPNAME}.lnk"
	# Try to remove the Start Menu folder - this will only happen if it is empty
	rmDir "$SMPROGRAMS\${COMPANYNAME}"
 
	# Remove files
	rmDir /r "$INSTDIR\${PROJECT_ARTIFACT_ID}"
	#delete conf file	
	delete $INSTDIR\application.conf
 
	# Always delete uninstaller as the last action
	delete $INSTDIR\uninstall.exe
 
	# Try to remove the install directory - this will only happen if it is empty
	rmDir $INSTDIR
 
	# Remove uninstaller information from the registry
	DeleteRegKey HKLM "${PRODUCT_REG_KEY_UNINST}${COMPANYNAME} ${APPNAME}"
	#Remove pipeline home 
	DeleteRegKey HKLM "Software\${APPNAME}"
	
sectionEnd
