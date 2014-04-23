@echo off
java -jar agm-data-generator.jar %*

if errorlevel 101 goto upgrade
exit /b

:upgrade
if exist tmp/agm-data-generator.jar (
  echo An upgrade downloaded successfully. Installing new version.

  rem Delete old backup, backup the previous version of generator, replace it with the newly downloaded version
  del tmp/old-agm-data-generator.jar 2> NUL
  move agm-data-generator.jar tmp\old-agm-data-generator.jar > NUL
  move tmp\agm-data-generator.jar agm-data-generator.jar > NUL

  rem Keep log file from the upgrade as upgrade-log.txt file
  del logs\upgrade-log.txt 2> NUL
  move logs\log.txt logs\upgrade-log.txt > NUL

  rem Start the data generator again; it'll check for an upgrade again but should upgrade nothing this time
  echo Starting new version.
  java -jar agm-data-generator.jar %*

) else (
  echo No upgrade found. Verify logs at logs folder.
)