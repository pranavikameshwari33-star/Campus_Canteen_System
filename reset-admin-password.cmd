@echo off
rem Sets a new password for an EXISTING canteen administrator on the LOCAL MongoDB server.
rem Usage:  reset-admin-password.cmd --admin-id CANTEENADMIN01 --database campus_canteen
rem Uses the MONGODB_URI environment variable. See README.md, "Forgotten administrator password".
setlocal
set "JAR=%~dp0target\campus-canteen-1.0.0.jar"
if not exist "%JAR%" (
  echo The application has not been built yet. First run:  mvnw.cmd -DskipTests package
  exit /b 1
)
java -Dloader.main=com.campuscanteen.tools.AdminPasswordReset -cp "%JAR%" org.springframework.boot.loader.launch.PropertiesLauncher %*
