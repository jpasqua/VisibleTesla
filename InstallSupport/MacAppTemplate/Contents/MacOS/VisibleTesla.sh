#!/bin/sh

PRG=$0
while [ -h "$PRG" ]; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '^.*-> \(.*\)$' 2>/dev/null`
    if expr "$link" : '^/' 2> /dev/null >/dev/null; then
        PRG="$link"
    else
        PRG="`dirname "$PRG"`/$link"
    fi
done

OLD_IFS=$IFS
IFS="@"
progdir=`dirname "$PRG"`
cd $progdir/../../..
IFS=$OLD_IFS

if [ ! -f /Library/Internet\ Plug-Ins/JavaAppletPlugin.plugin/Contents/Home/bin/java ]; then
  osascript <<EOF
    tell app "System Events" to display dialog \
    "Unable to locate Java\n" & \
    "Check your installation at java.com/verify" \
    with title "VisibleTesla"
EOF
  exit -1
fi

VM_PARAMS=""
VM_PARAMS_FILE=$progdir/vmparams.txt
if [ -f $VM_PARAMS_FILE ]; then
  VM_PARAMS=" `cat $VM_PARAMS_FILE` "
fi

exec /Library/Internet\ Plug-Ins/JavaAppletPlugin.plugin/Contents/Home/bin/java -jar $VM_PARAMS -Xdock:icon=VTIcon.icns VisibleTesla.jar > ~/apprun.txt
