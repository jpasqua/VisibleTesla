REM
REM Tests whether there's a locally installed Java and uses it
REM

	IF NOT EXIST .\jre1.7 GOTO NO_LOCAL_JAVA
	start .\jre1.7\bin\javaw -Xmx1024m -jar VisibleTesla.jar
	GOTO DONE
:NO_LOCAL_JAVA
	start javaw -Xmx1024m -jar VisibleTesla.jar
:DONE