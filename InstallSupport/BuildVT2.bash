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

    # NOTE: UPDATE THIS VALUE!!!
    VERSION="0.50.03"

# Build the zip files for each platform
    cd $PROJECT_ROOT
    ant package-mac
    ant package-win
    ant package-generic


# Place the new zip file for each platform in the right spot
    mv dist/VTMac.zip     $RELEASE_ROOT/MacApp/VT_$VERSION.zip
    mv dist/VTWin.zip     $RELEASE_ROOT/WinApp/VT_$VERSION.zip
    mv dist/VTGeneric.zip $RELEASE_ROOT/Generic/VT_$VERSION.zip

