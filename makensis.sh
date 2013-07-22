#!/bin/bash
mvn nsis:generate-headerfile
mvn nsis:make -Dnsis.scriptfile=nsis/installer.nsi
