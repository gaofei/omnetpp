@echo off
rem
rem usage: runtest [<testfile>...]
rem without args, runs all *.test files in the current directory
rem

set TESTFILES=%*
if "x%TESTFILES%" == "x" set TESTFILES=*.test

mkdir work 2>nul
call opp_test %OPT% -g -v %TESTFILES% || goto end

cd work || goto end
echo > .project

echo Creating makefiles...
cd %~dp0\work
for /d %%i in (*) do echo %%i && cd %~dp0\work\%%i && nmake "OPP_MAKEMAKE=call opp_nmakemake -f --out ../out" -f buildspec || echo MAKEFILE CREATION FAILED: %%i

echo Build...
cd %~dp0\work
for /d %%i in (*) do echo %%i && cd %~dp0\work\%%i && nmake -f Makefile.vc || echo BUILD FAILED: %%i

echo Running the tests...
cd %~dp0
call opp_test %OPT% -r -v %TESTFILES% || goto end

echo.
echo Results can be found in work/

:end