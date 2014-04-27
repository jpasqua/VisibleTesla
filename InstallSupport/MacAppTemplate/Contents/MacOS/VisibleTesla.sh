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

progdir=`dirname "$PRG"`

if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
elif [ -x /usr/libexec/java_home ]; then
  JAVACMD="`/usr/libexec/java_home`/bin/java"
else
  JAVACMD="/Library/Internet Plug-Ins/JavaAppletPlugin.plugin/Contents/Home/bin/java"
fi

OLDIFS=$IFS
IFS="@"
VM_PARAMS=""
VM_PARAMS_FILE=$progdir/vmparams.txt
if [ -f $VM_PARAMS_FILE ]; then
  VM_PARAMS=" `cat $VM_PARAMS_FILE` "
fi
cd $progdir/../../..
IFS=$OLDIFS
exec $JAVACMD -jar $VM_PARAMS -Xdock:icon=VTIcon.icns VisibleTesla.jar

