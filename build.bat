cd %~dp0
del /s /q objs
rmdir objs
md objs
pushd src\main\java
dir /s /B *.java > "%~dp0sources.txt"
javac -source 8 -target 8 -d ..\..\..\objs "@%~dp0sources.txt"
popd
pushd objs
copy ..\src\main\resources\icon.png icon.png
jar cfm ..\nox.jar ..\src\main\resources\META-INF\MANIFEST.MF nitrous icon.png
