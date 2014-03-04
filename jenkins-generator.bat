@echo off
if exist C:\ALI (
  cd C:\ALI\data-generator
)
if exist C:\AgM (
  cd C:\AgM\data-generator
)

setlocal enableextensions enabledelayedexpansion
if not "%account-name%"=="" set account-name="--account-name=%account-name%"
if not "%solution-name%"=="" set solution-name="--solution-name=%solution-name%"
if not "%tenant-url%"=="" set tenant-url="--tenant-url=%tenant-url%"

if "%what-to-generate%"=="All" set what-to-generate=--generate-uphb
if "%what-to-generate%"=="Skip users" set what-to-generate=--generate-phb

if "%delete-all%"=="true" set delete-all=--delete-all
if "%delete-all%"=="false" set delete-all=

if exist "%workspace%\excel-data" (
  set custom-data="%workspace%\custom-data.xlsx"
  move "%workspace%\excel-data" "%workspace%\custom-data.xlsx" > nul
)

if "%wait-time%"=="" set wait_time=0
if not "%wait-time%"=="" set /A wait_time=%wait-time%*60


echo Starting following command: java -jar agm-data-generator.jar %custom-data% %what-to-generate% %account-name% %solution-name% %tenant-url% %delete-all% --force-delete %user-name% %password%

java -jar agm-data-generator.jar %custom-data% %what-to-generate% %account-name% %solution-name% %tenant-url% %delete-all% --force-delete %user-name% %password%

if %errorlevel% == 0 (
  echo Waiting %wait-time% mins to let ALI DevBrige to synchronize data from SVN and Jenkins into the populated tenant.
  ping localhost -n %wait_time% > nul
)