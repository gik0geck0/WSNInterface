all: build
build:
	ant debug

clean:
	ant clean

install:
	adb install -r bin/WSNInterface-debug.apk

log:
	adb logcat > logout.txt 2>&1 &

seelog:
	tail -F logout.txt | logcat-color

connect:
	#adb connect 192.168.0.50
	#adb connect 138.67.198.108
	#adb connect 192.168.210.99
	adb connect 192.168.1.16

logcat:
	adb logcat | logcat-color

bil: build install seelog
