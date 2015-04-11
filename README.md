#VisibleTesla

**NOTE** to existing users: Starting with version 0.31.00, VisibleTesla will do a one time transofrmation of data files to a new and enhanced format. This conversion is irreversible and the new format is not compatible with older versions. The older version of the data files will not be changed or deleted, but it is strongly suggested that they be backed up before moving to VisibleTesla version 0.31.00 or greater. Once you are successfully converted to the new data file format, you may remove the old files if you wish in order to save space.

An appliction to view, monitor, and control your Tesla Model S and in the future, other Tesla models.

This software and documentation do not come from Tesla Motors Inc.

*Be careful* when using this software as it can lock and unlock your car as well as control various functions relating to the charging system, sun roof, lights, horn, and other subsystems of the car.

*Be careful* not to send your login and password to anyone other than Tesla or you are giving away the authentication details required to control your car.

**NOTE:** This version depends on the "owner" interface to Tesla's servers and as a consequence requires the "Post6" version of TeslaClient.

#Disclaimer

Use this application at your own risk. The author does not guarantee its proper functioning. This application attempts to use the same interfaces used by the official Tesla apps. However, it is possible that use of this application may cause unexpected damage for which nobody but you are responsible. Use of this application can change the settings on your car and may have negative consequences such as (but not limited to) unlocking the doors, opening the sun roof, or reducing the available charge in the battery.

#Contributors
Joe Pasqua (https://github.com/jpasqua): Author  
Alex Karahalios (https://github.com/araxara): Mac App Bundling  
Sune Jakobsson (https://github.com/sunejak): Testing and Several Bug Fixes

#Preparing your build environment

This project assumes a directory structure that looks like this:

	Tesla					-- Overall container that may include other Tesla related projects
		TeslaClient   		-- The Tesla REST API Client
		VisibleTesla		-- This project
	ThirdParty				-- A repository for third party library dependencies
		apache
			commons-io-2.4
		javafx-dialogs
		jfxtras
		jexcelapi
		google-guava
		appbundler-1.0.jar	-- Optional if you want to create Mac OS X bundled application

The Tesla/VisibleTesla directory corresponds to this github project (VisibleTesla.git). The TeslaClient directory corresponds to a companion project which is the Tesla REST API implementation. That project can be found here:
[TeslaClient](https://github.com/jpasqua/TeslaClient.git)

Once you have installed the TeslaClient project you'll have most of what you need. To get the rest, you can use the following commands to populate the hierarchy. It assumes that:

+ <code>$ROOT</code>is the directory in which the top level Tesla directory will reside.

Be sure to either set these variables or adapt the commands below. Note that the jfxtras library is under active development and the library versions change fairly frequently. The project refers to a stable jar name which is assumed to be linked to the version that has been downloaded. The commands below create the link.

	cd $ROOT
    mkdir Tesla
    cd Tesla
    git clone https://github.com/jpasqua/VisibleTesla.git
	mkdir $ROOT/ThirdParty/javafx-dialogs
	mkdir $ROOT/ThirdParty/jfxtras
	mkdir $ROOT/ThirdParty/jexcelapi
	mkdir $ROOT/ThirdParty/google-guava
	mkdir $ROOT/ThirdParty/apache

	# Download the apache libraries
	cd $ROOT/ThirdParty/apache
	curl -s -O http://mirror.nexcess.net/apache//commons/io/binaries/commons-io-2.4-bin.zip
	unzip commons-io-2.4-bin.zip
	rm commons-io-2.4-bin.zip

	# Download the javafx-dialogs library
	cd $ROOT/ThirdParty/javafx-dialogs
	# curl -s -O -L https://github.com/marcojakob/javafx-ui-sandbox/blob/master/javafx-dialogs/dist/javafx-dialogs-0.0.3.jar?raw=true
	curl -s -O https://dl.dropboxusercontent.com/u/7045813/VisibleTesla/jars/javafx-dialogs-0.0.3JP.jar

	# Download the  JExcelAPI library
	cd $ROOT/ThirdParty/jexcelapi
	curl -s -O -L http://sourceforge.net/projects/jexcelapi/files/jexcelapi/2.6.12/jexcelapi_2_6_12.zip
	unzip jexcelapi_2_6_12.zip
	rm jexcelapi_2_6_12.zip

	# Download Google Guava
	cd $ROOT/ThirdParty/google-guava
    curl -s -O -L http://search.maven.org/remotecontent?filepath=com/google/guava/guava/18.0/guava-18.0.jar

	# Download the jfxtras library
	# There may be a newer version of the library. If so, update the version details below
	cd $ROOT/ThirdParty/jfxtras
	curl -s -O https://dl.dropboxusercontent.com/u/7045813/VisibleTesla/jars/jfxtras-labs-2.2.jar

	# Download cron4j
	cd $ROOT/ThirdParty
	curl -s -O -L https://sourceforge.net/projects/cron4j/files/cron4j/2.2.5/cron4j-2.2.5.zip
	unzip cron4j-2.2.5.zip
	rm cron4j-2.2.5.zip

	# The Java application bundler file is only used to create a Mac OS X bundled app. It's not used by VisibleTesla at run time
	cd $ROOT/ThirdParty
	curl -s -O -L https://java.net/projects/appbundler/downloads/download/appbundler-1.0.jar


#Installing and Running VisibleTesla

Once you've built the application, you can run it simply by double-clicking <code>VisibleTesla.jar</code>in Tesla/VisibleTesla/dist. For details, refer to the documentation and release notes in Tesla/VisibleTesla/Documentation.

#Building a Mac OS X Bundled Application

You can build a version of VisibleTesla that has the Java runtime bundled and is a self-contained Mac OS X application. To build VisibleTesla.app, you need to set an environment variable that identifies the JVM you want to be bundled in. To do that, just point JAVA_HOME to the desired location. Once you've done that, you can build the bundle-VisibleTesla target. The following commands will do that for you:

	export JAVA_HOME=`/usr/libexec/java_home`
	ant bundle-VisibleTesla

If you're a registered Apple developer and have your identity and signing certificates installed, you can execute the following command after building the app:

	export MAC_SIGNER="Developer ID Application: <Your Developer ID>" 
	ant sign-mac

If you want to build the app, sign it, and bundle it into a zip file, just set the environment variables as above an invoke the package-mac target:

	ant package-mac
	
**Notes**:
+ If you don't set MAC_SIGNER you can still use package-mac to create a zip file. You'll see a harmless warning saying that the codesign failed.   
+ The "stub" Mac application has been removed. Use this real version instead.

#Building a Windows Zip file

You can create a zip file that includes a Java VM and some additional windows scripts that make it more convenient to install and use VisibleTesla on a Windows machine. Just use the following command:

		ant package-windows

When the resulting zip file is extracted on a Windows machine, you'll see a file called "Make_Windows_Shortcut". Double click that to put a VisibleTesla icon on your desktop that will launch the app.


#Building a Linux Zip file

You can create a zip file that contains jsut the bare essentials for a Linux installation:

		ant package-linux

It does *not* include a pre-packaged Java VM.



