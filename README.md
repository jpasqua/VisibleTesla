#VisibleTesla

An appliction to view, monitor, and control your Tesla Model S and in the future, other Tesla models.

This software and documentation do not come from Tesla Motors Inc.

*Be careful* when using this software as it can lock and unlock your car as well as control various functions relating to the charging system, sun roof, lights, horn, and other subsystems of the car.

*Be careful* not to send your login and password to anyone other than Tesla or you are giving away the authentication details required to control your car.

#Disclaimer

Use this application at your own risk. The authors do not guarantee its proper functioning. This application attempts to use the same interfaces used by the official Tesla apps. However, it is possible that use of this application may cause unexpected damage for which nobody but you are responsible. Use of this application can change the settings on your car and may have negative consequences such as (but not limited to) unlocking the doors, opening the sun roof, or reducing the available charge in the battery.

#Contributors
[Joe Pasqua](https://github.com/jpasqua)

#Preparing your build environment

This project assumes a directory structure that looks like this:

	Tesla					-- Overall container that may include other Tesla related projects
		TeslaClient   		-- The Tesla REST API Client
		VisibleTesla		-- This project
	ThirdParty				-- A repository for third party library dependencies
		apache
			commons-io-2.4
		javafx-dialog
		jfxtras
		jexcelapi
		google-guava
		appbundler-1.0.jar	-- Optional if you want to create Mac OS X bundled application

The Tesla/VisibleTesla directory corrsponds to this github project (VisibleTesla.git). The TeslaClient directory corresponds to a companion project which is the Tesla REST API implementation. That project can be found here:
[TeslaClient](https://github.com/jpasqua/TeslaClient.git)

Once you have installed the TeslaClient project you'll have most of what you need. To get the rest, you can use the following commands to populate the hierarchy. It assumes that:

+ <code>$DOWNLOAD</code>is the directory where you downloaded the project from github
+ <code>$ROOT</code>is the directory in which the top level Tesla directory resides.

Be sure to either set these variables or adapt the commands below. Note that the jfxtras library is under active development and the library versions change fairly frequently. The project refers to a stable jar name which is assumed to be linked to the version that has been downloaded. The commands below create the link.

	cd $ROOT
	mv $DOWNLOAD/VisibleTesla-master Tesla/VisibleTesla
	mkdir ThirdParty/javafx-dialogs
	mkdir ThirdParty/jfxtras
	mkdir ThirdParty/jexcelapi
	mkdir ThirdParty/google-guava

	# Download the apache libraries
	cd ThirdParty/apache
	curl -s -O http://mirror.nexcess.net/apache//commons/io/binaries/commons-io-2.4-bin.zip
	unzip commons-io-2.4-bin.zip
	rm commons-io-2.4-bin.zip

	# Download the javafx-dialogs library
	cd ../javafx-dialogs
	# curl -s -O -L https://github.com/marcojakob/javafx-ui-sandbox/blob/master/javafx-dialogs/dist/javafx-dialogs-0.0.3.jar?raw=true
	curl -s -O https://dl.dropboxusercontent.com/u/7045813/VisibleTesla/javafx-dialogs-0.0.3JP.jar
	mv javafx-dialogs-0.0.3.jar* javafx-dialogs-0.0.3.jar

	# Download the  JExcelAPI library
	cd ../jexcelapi
	curl -s -O -L http://sourceforge.net/projects/jexcelapi/files/jexcelapi/2.6.12/jexcelapi_2_6_12.zip
	unzip jexcelapi_2_6_12.zip
	rm jexcelapi_2_6_12.zip

	# Download Google Guava
	cd ../google-guava
	curl -s -O http://search.maven.org/remotecontent?filepath=com/google/guava/guava/15.0/guava-15.0.jar

	# Download the jfxtras library
	# There may be a newer version of the library. If so, update the version details below
	cd ../jfxtras
	curl -s -O https://oss.sonatype.org/content/repositories/snapshots/org/jfxtras/jfxtras-labs/2.2-r6-SNAPSHOT/jfxtras-labs-2.2-r6-20130815.133831-3.jar
    ln -s jfxtras-labs-2.2-r6-20130815.133831-3.jar jfxtras-labs-2.2.jar

	# If you want to create a Mac OS X bundled application, you will need the Java application bundler file.
	cd ..
	curl -s -O  https://java.net/projects/appbundler/downloads/download/appbundler-1.0.jar


#Installing and Running VisibleTesla

Once you've built the application, you can run it simply by double-clicking <code>VisibleTesla.jar</code>in Tesla/VisibleTesla/dist. For details, refer to the documentation and release notes in Tesla/VisibleTesla/Documentation.

#Building Mac OS X Bunddled Clickable Application

You can build a version of VisibleTesla that has the Java runtime bundled and is a self contained Mac OS X application by issuing the command

	ant bundle-VisableTesla
	
Furthermore, if you wish to distribute the application, you can also codesign it by issuing the command

	codesign -s "Developer ID Application: <Your Developer ID>"  dist/VisableTesla.app --deep
	
