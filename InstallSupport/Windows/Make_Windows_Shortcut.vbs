Set wsc = WScript.CreateObject("WScript.Shell")
'Set fso = CreateObject ("Scripting.FileSystemObject")
'Set stdout = fso.GetStandardStream (1)

Set lnk = wsc.CreateShortcut(wsc.SpecialFolders("desktop") & "\VisibleTesla.LNK")
lnk.targetpath = wsc.CurrentDirectory & "\vtrunner.bat"
'stdout.WriteLine lnk.targetpath
lnk.description = "Run VisibleTesla"
lnk.workingdirectory = wsc.CurrentDirectory
lnk.IconLocation = wsc.CurrentDirectory & "\FobIcon.ico, 0"
lnk.save
