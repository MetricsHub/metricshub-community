Summary: APPLICATION_SUMMARY
Name: APPLICATION_PACKAGE
Version: APPLICATION_VERSION
Release: APPLICATION_RELEASE
License: APPLICATION_LICENSE_TYPE
Vendor: APPLICATION_VENDOR

%if "xAPPLICATION_URL" != "x"
URL: APPLICATION_URL
%endif

%if "xAPPLICATION_PREFIX" != "x"
Prefix: APPLICATION_PREFIX
%endif

Provides: APPLICATION_PACKAGE

%if "xAPPLICATION_GROUP" != "x"
Group: APPLICATION_GROUP
%endif

Autoprov: 0
Autoreq: 0

Requires: /bin/sh, systemd, coreutils, glibc, grep

#comment line below to enable effective jar compression
#it could easily get your package size from 40 to 15Mb but
#build time will substantially increase and it may require unpack200/system java to install
%define __jar_repack %{nil}

%define package_filelist %{_tmppath}/%{name}.files
%define app_filelist %{_tmppath}/%{name}.app.files
%define filesystem_filelist %{_tmppath}/%{name}.filesystem.files

%define default_filesystem / /opt /usr /usr/bin /usr/lib /usr/local /usr/local/bin /usr/local/lib

%description
APPLICATION_DESCRIPTION

%global __os_install_post %{nil}

%prep

%build

%install
rm -rf %{buildroot}
install -d -m 755 %{buildroot}APPLICATION_DIRECTORY
cp -r %{_sourcedir}APPLICATION_DIRECTORY/* %{buildroot}APPLICATION_DIRECTORY
if [ "$(echo %{_sourcedir}/lib/systemd/system/*.service)" != '%{_sourcedir}/lib/systemd/system/*.service' ]; then
  install -d -m 755 %{buildroot}/lib/systemd/system
  cp %{_sourcedir}/lib/systemd/system/*.service %{buildroot}/lib/systemd/system
fi
%if "xAPPLICATION_LICENSE_FILE" != "x"
  %define license_install_file %{_defaultlicensedir}/%{name}-%{version}/%{basename:APPLICATION_LICENSE_FILE}
  install -d -m 755 "%{buildroot}%{dirname:%{license_install_file}}"
  install -m 644 "APPLICATION_LICENSE_FILE" "%{buildroot}%{license_install_file}"
%endif
(cd %{buildroot} && find . -path ./lib/systemd -prune -o -type d -print) | sed -e 's/^\.//' -e '/^$/d' | sort > %{app_filelist}
{ rpm -ql filesystem || echo %{default_filesystem}; } | sort > %{filesystem_filelist}
comm -23 %{app_filelist} %{filesystem_filelist} > %{package_filelist}
sed -i -e 's/.*/%dir "&"/' %{package_filelist}
(cd %{buildroot} && find . -not -type d) | sed -e 's/^\.//' -e 's/.*/"&"/' >> %{package_filelist}
%if "xAPPLICATION_LICENSE_FILE" != "x"
  sed -i -e 's|"%{license_install_file}"||' -e '/^$/d' %{package_filelist}
%endif

%files -f %{package_filelist}
%if "xAPPLICATION_LICENSE_FILE" != "x"
  %license "%{license_install_file}"
%endif

%post
package_type=rpm
LAUNCHER_AS_SERVICE_SCRIPTS
DESKTOP_COMMANDS_INSTALL
LAUNCHER_AS_SERVICE_COMMANDS_INSTALL

%pre
package_type=rpm
LAUNCHER_AS_SERVICE_SCRIPTS
if [ "$1" = 2 ]; then
  true; LAUNCHER_AS_SERVICE_COMMANDS_UNINSTALL
fi

%preun

if [ -L /etc/systemd/system/multi-user.target.wants/metricshub-enterprise-service.service ]; then
  rm -f /etc/systemd/system/multi-user.target.wants/metricshub-enterprise-service.service
fi

if [ -L /etc/systemd/system/metricshub-enterprise-service.service ]; then
  rm -f /etc/systemd/system/metricshub-enterprise-service.service
fi

package_type=rpm
DESKTOP_SCRIPTS
LAUNCHER_AS_SERVICE_SCRIPTS
DESKTOP_COMMANDS_UNINSTALL
LAUNCHER_AS_SERVICE_COMMANDS_UNINSTALL

if [ -d APPLICATION_DIRECTORY ]; then
  if [ -L APPLICATION_DIRECTORY/config ]; then
    rm -f APPLICATION_DIRECTORY/config
  fi

  if [ -L APPLICATION_DIRECTORY/connectors ]; then
    rm -f APPLICATION_DIRECTORY/connectors
  fi

  if [ -L APPLICATION_DIRECTORY/security ]; then
    rm -f APPLICATION_DIRECTORY/security
  fi

  if [ -L APPLICATION_DIRECTORY/otel ]; then
    rm -f APPLICATION_DIRECTORY/otel
  fi

  if [ -L APPLICATION_DIRECTORY/logs ]; then
    rm -f APPLICATION_DIRECTORY/logs
  fi

  if [ -L APPLICATION_DIRECTORY/LICENSE ]; then
    rm -f APPLICATION_DIRECTORY/LICENSE
  fi
  
  if [ -L APPLICATION_DIRECTORY/extensions ]; then
    rm -f APPLICATION_DIRECTORY/extensions
  fi
fi

%clean

%posttrans

if [ -d APPLICATION_DIRECTORY ]; then
  if [ ! -e  APPLICATION_DIRECTORY/config ]; then
    cd APPLICATION_DIRECTORY && ln -s lib/config config
  fi

  if [ ! -e  APPLICATION_DIRECTORY/connectors ]; then
    cd APPLICATION_DIRECTORY && ln -s lib/connectors connectors
  fi
 
  if [ ! -e  APPLICATION_DIRECTORY/security ]; then
    cd APPLICATION_DIRECTORY && ln -s lib/security security
  fi

  if [ ! -e  APPLICATION_DIRECTORY/otel ]; then
    cd APPLICATION_DIRECTORY && ln -s lib/otel otel
  fi

  if [ ! -e  APPLICATION_DIRECTORY/logs ]; then
    cd APPLICATION_DIRECTORY && ln -s lib/logs logs
  fi

  if [ ! -e  APPLICATION_DIRECTORY/LICENSE ]; then
    cd APPLICATION_DIRECTORY && ln -s lib/LICENSE LICENSE
  fi
  
  if [ ! -e  APPLICATION_DIRECTORY/extensions ]; then
    cd APPLICATION_DIRECTORY && ln -s lib/extensions extensions
  fi
fi
