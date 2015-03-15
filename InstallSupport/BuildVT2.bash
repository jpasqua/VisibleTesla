#!/bin/bash
#
# The VisibleTesla release process
#

# Set up environement variables
    export JAVA_HOME=`/usr/libexec/java_home`
    export MAC_SIGNER="Developer ID Application: Joe Pasqua"

# Set up local variables
    PROJECT_ROOT=~/Dropbox/Dev/Tesla/VisibleTesla

    RELEASE_ROOT=~/Dropbox/Public/VT2
    # For testing purposes, uncomment the line below to override RELASE_ROOT 
    # RELEASE_ROOT=~/Desktop/Temp/VisibleTesla

# Build the zip files for each platform
    cd $PROJECT_ROOT
    ant package-mac
    ant package-win
    ant package-generic

# Place the new zip file for each platform in the right spot
    mv dist/VTMac.zip       $RELEASE_ROOT/MacApp/VisibleTesla.zip
    mv dist/VTWin.zip       $RELEASE_ROOT/WinApp/VisibleTesla.zip
    mv dist/VTGeneric.zip   $RELEASE_ROOT/RawApp/VisibleTesla.zip

# Create a copy of the newly released version which is named with it's version #
# This should be done with symlinks, but DropBox chokes on them
    cp $RELEASE_ROOT/MacApp/VisibleTesla.zip    $RELEASE_ROOT/MacApp/VT_$VERSION.zip
    cp $RELEASE_ROOT/WinApp/VisibleTesla.zip    $RELEASE_ROOT/WinApp/VT_$VERSION.zip
    cp $RELEASE_ROOT/RawApp/VisibleTesla.zip    $RELEASE_ROOT/RawApp/VT_$VERSION.zip

# Update the FirmwareVersion.properties file
    cp $PROJECT_ROOT/src/org/noroomattheinn/visibletesla/FirmwareVersions.properties $RELEASE_ROOT

