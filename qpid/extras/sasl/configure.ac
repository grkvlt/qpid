dnl Process this file with autoconf to produce a configure script.
dnl
dnl This file is free software; as a special exception the author gives
dnl unlimited permission to copy and/or distribute it, with or without
dnl modifications, as long as this notice is preserved.
dnl
dnl This program is distributed in the hope that it will be useful, but
dnl WITHOUT ANY WARRANTY, to the extent permitted by law; without even the
dnl implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
dnl
dnl When updating the name/version number here, also update it in
dnl src/qpid/Version.h

AC_INIT([saslwrapper], [0.1], [dev@qpid.apache.org])
AC_CONFIG_AUX_DIR([build-aux])
AM_INIT_AUTOMAKE([dist-bzip2 subdir-objects])

# Minimum Autoconf version required.
AC_PREREQ(2.59)

AC_CONFIG_HEADERS([config.h])

AC_PROG_CC_STDC
AM_PROG_CC_C_O
AC_PROG_CXX
AC_LANG([C++])	

# Check for optional use of help2man
AC_CHECK_PROG([HELP2MAN], [help2man], [help2man])
AC_ARG_WITH([help2man],
  [AS_HELP_STRING([--with-help2man], [Use help2man to generate man pages.])],
  [case "$withval" in
    yes) test -z "$HELP2MAN" && AC_MSG_ERROR([help2man not found.]) ;;
    no) HELP2MAN="" ;;
    *)   AC_MSG_ERROR([Bad value ${withval} for --with-help2man.]) ;;
   esac])
AM_CONDITIONAL([HAVE_HELP2MAN], [test -n "$HELP2MAN"])

# Check for optional use of doxygen
AC_CHECK_PROG([DOXYGEN], [doxygen], [doxygen])
AC_ARG_WITH([doxygen],
  [AS_HELP_STRING([--with-doxygen], [Use doxygen to generate API documentation.])],
  [case "$withval" in
    yes) test -z "$DOXYGEN" && AC_MSG_ERROR([doxygen not found.]) ;;
    no) DOXYGEN="" ;;
    *)   AC_MSG_ERROR([Bad value ${withval} for --with-doxygen.]) ;;
   esac])
AM_CONDITIONAL([HAVE_DOXYGEN], [test -n "$DOXYGEN"])

AC_ARG_ENABLE(warnings,
[  --enable-warnings   turn on lots of compiler warnings (recommended)],
[case "${enableval}" in
   yes|no) ;;
   *)  AC_MSG_ERROR([bad value ${enableval} for warnings option]) ;;
 esac],
 [enableval=yes])

# Set up for gcc as compiler
if test x$GXX = xyes; then
	# Warnings: Enable as many as possible, keep the code clean. Please
	# do not disable warnings or remove -Werror without discussing on
	# qpid-dev list.
	#
	# The following warnings are deliberately omitted, they warn on valid code.
	# -Wunreachable-code -Wpadded -Winline
	# -Wshadow - warns about boost headers.
	if test "${enableval}" = yes; then
	    gl_COMPILER_FLAGS(-Werror)
	    gl_COMPILER_FLAGS(-pedantic)
	    gl_COMPILER_FLAGS(-Wall)
	    gl_COMPILER_FLAGS(-Wextra)
	    gl_COMPILER_FLAGS(-Wno-shadow)
	    gl_COMPILER_FLAGS(-Wpointer-arith)
	    gl_COMPILER_FLAGS(-Wcast-qual)
	    gl_COMPILER_FLAGS(-Wcast-align)
	    gl_COMPILER_FLAGS(-Wno-long-long)
	    gl_COMPILER_FLAGS(-Wvolatile-register-var)
	    gl_COMPILER_FLAGS(-Winvalid-pch)
	    gl_COMPILER_FLAGS(-Wno-system-headers)
	    gl_COMPILER_FLAGS(-Woverloaded-virtual)	
	    AC_SUBST([WARNING_CFLAGS], [$COMPILER_FLAGS])
	    AC_DEFINE([lint], 1, [Define to 1 if the compiler is checking for lint.])
	    COMPILER_FLAGS=
	fi
else
	AC_CHECK_DECL([__SUNPRO_CC], [SUNCC=yes], [SUNCC=no])
	
	# Set up for sun CC compiler
	if test x$SUNCC = xyes; then
		if test "${enableval}" = yes; then
			WARNING_FLAGS=+w
		fi
		CXXFLAGS="$CXXFLAGS -library=stlport4 -mt"
		LD="$CXX"
		LDFLAGS="$LDFLAGS -library=stlport4 -mt"
		AC_SUBST([SUNCC_RUNTIME_LIBS], [-lCrun])
	fi
fi

AC_DISABLE_STATIC
AC_PROG_LIBTOOL
AC_SUBST([LIBTOOL_DEPS])

# For libraries (libcommon) that use dlopen, dlerror, etc.,
# test whether we need to link with -ldl.
gl_saved_libs=$LIBS
  AC_SEARCH_LIBS(dlopen, [dl],
		 [test "$ac_cv_search_dlopen" = "none required" ||
		  LIB_DLOPEN=$ac_cv_search_dlopen])
  AC_SUBST([LIB_DLOPEN])
LIBS=$gl_saved_libs

# Set the argument to be used in "libtool -version-info ARG".
QPID_CURRENT=2
QPID_REVISION=0
QPID_AGE=0
LIBTOOL_VERSION_INFO_ARG=$QPID_CURRENT:$QPID_REVISION:$QPID_AGE
AC_SUBST(LIBTOOL_VERSION_INFO_ARG)

gl_CLOCK_TIME

# Enable Valgrind	
AC_ARG_ENABLE([valgrind],
  [AS_HELP_STRING([--enable-valgrind],
    [run valgrind memory checker on tests, if available (default yes)])],
  [case $enableval in
    yes|no) enable_VALGRIND=$enableval;;
    *) AC_MSG_ERROR([Invalid value for --enable-valgrind: $enableval]);;
   esac],
  [enable_VALGRIND=yes]
)

# We use valgrind for the tests.  See if it's available.
# Check for it unconditionally, so we don't have to duplicate its
# use of AC_SUBST([VALGRIND]).
AC_CHECK_PROG([VALGRIND], [valgrind], [valgrind])
test "$enable_VALGRIND" = no && VALGRIND=

# If rpmlint is available we'll run it when building RPMs.
AC_CHECK_PROG([RPMLINT], [rpmlint], [rpmlint])
AM_CONDITIONAL([HAS_RPMLINT], [test -n "$RPMLINT"])

# Code generation: generated code is included in the distribution
# so code generation is only required in an svn checkout.
# It requires several external tools and files, which we check for here.

AC_CHECK_PROG([RUBY], [ruby], [ruby])
test -n "$RUBY" && generate=yes
test -z "$RUBY" && AC_MSG_ERROR([Missing ruby installation (try "yum install ruby").])

# Swig binding generator is needed for the script (Ruby, Python, etc.) bindings.
AC_PROG_SWIG(1.3.26)
test ! -x "$SWIG" && SWIG=""
AC_ARG_WITH([swig],
  [AS_HELP_STRING([--with-swig], [Use swig to generate qmf bindings.])],
  [case "$withval" in
    yes) test -z "$SWIG" && AC_MSG_ERROR([swig not found.]) ;;
    no) SWIG="" ;;
    *)   AC_MSG_ERROR([Bad value ${withval} for --with-swig.]) ;;
   esac])
AM_CONDITIONAL([HAVE_SWIG], [test -n "$SWIG"])

# Ruby bindings: To build ruby wrappers, the ruby-devel files must be present.

AC_PATH_PROGS(RUBY, [ruby1.8 ruby], [])
AC_ARG_VAR(RUBY, [Ruby interpreter])
if test -n "$RUBY" ; then
   AC_ARG_VAR(RUBY_INC, [Directory where ruby.h can be found])
   if test -z "$RUBY_INC" ; then
      [RUBY_INC=`$RUBY -rrbconfig -e 'puts Config::CONFIG["rubyhdrdir"] || Config::CONFIG["archdir"]'`]
   fi
   AC_SUBST(RUBY_INC)

   AC_ARG_VAR(RUBY_INC_ARCH, [Directory where ruby/config.h can be found (needed from Ruby 1.9)])
   if test -z "$RUBY_INC_ARCH" ; then
      [RUBY_INC_ARCH=`$RUBY -rrbconfig -e 'd = Config::CONFIG["rubyhdrdir"];if d != nil; print d + "/" + Config::CONFIG["arch"]; end'`]
      dnl For earlier versions, just make it the same as RUBY_INC.
      test x"$RUBY_INC_ARCH" != x || RUBY_INC_ARCH=$RUBY_INC
   fi
   AC_SUBST(RUBY_INC_ARCH)
   AC_ARG_VAR(RUBY_LIB, [Directory to install ruby files into])
      if test -z "$RUBY_LIB" ; then
      dnl Kludge to install ruby files under $prefix
      [RUBY_LIB=`$RUBY -rrbconfig -e 'puts Config::CONFIG["sitelibdir"].gsub("/usr", "${prefix}")'`]
   fi
   AC_SUBST(RUBY_LIB)

	AC_ARG_VAR(RUBY_LIB_ARCH, [Directory to install ruby binary modules into])
	if test -z "$RUBY_LIB_ARCH" ; then
      dnl Kludge to install ruby files under $prefix
	  [RUBY_LIB_ARCH=`$RUBY -rrbconfig -e 'puts Config::CONFIG["sitearchdir"].gsub("/usr", "${prefix}")'`]
	fi
	AC_SUBST(RUBY_LIB_ARCH)

	RUBY_LIBS=
	case $host_os in
	cygwin*) RUBY_LIBS=-lruby ;;
	esac
	AC_SUBST(RUBY_LIBS)

   RUBY_DLEXT=`$RUBY -rrbconfig -e 'puts Config::CONFIG[["DLEXT"]]'`
   AC_SUBST(RUBY_DLEXT)
fi
AM_CONDITIONAL([HAVE_RUBY_DEVEL], [test -f $RUBY_INC/ruby.h && test -n "$SWIG"])

# Python bindings: To build python wrappers, the ruby-devel files must be present.

AM_PATH_PYTHON()
if test -n "$PYTHON" ; then
    AC_MSG_CHECKING([$PYTHON include dir])
    if $PYTHON -c 'import distutils.sysconfig' 2>/dev/null ; then
        PYTHON_INC=`$PYTHON -c 'import os,distutils.sysconfig;print(distutils.sysconfig.get_python_inc().replace(os.sep,"/"))'`
	AC_SUBST(PYTHON_INC)
    else
        if test yes = "$with_python" ; then
	    AC_MSG_ERROR([Couldn't import Python module distutils.sysconfig - you probably need to install a python-dev or python-devel package])
	else
	    AC_MSG_WARN([Couldn't import Python module distutils.sysconfig - you probably don't have a python-dev or python-devel package installed])
	fi
    fi
    AC_MSG_RESULT([$PYTHON_INC])
    AC_MSG_CHECKING([for directory to install python bindings in])
    if test -z "$PYTHON_LIB" ; then
	  PYTHON_LIB=`$PYTHON -c 'import os,distutils.sysconfig;print(distutils.sysconfig.get_python_lib(1).replace(os.sep,"/"))'`
    fi
    AC_MSG_RESULT([$PYTHON_LIB])
    AC_ARG_VAR(PYTHON_LIB, [Directory to install python bindings in])

    AC_MSG_CHECKING([for python libraries to link against])
	PYTHON_LIBS=`$PYTHON -c 'import os,sys;print("-L"+os.path.join(sys.path[[3]],"config")+" -lpython"+sys.version[[:3]])'`
    AC_SUBST(PYTHON_LIBS)
    AC_MSG_RESULT([$PYTHON_LIBS])
fi
AM_CONDITIONAL([HAVE_PYTHON_DEVEL], [test -f $PYTHON_INC/Python.h && test -n "$SWIG"])


LIBS=$tmp_LIBS

# Setup --with-sasl/--without-sasl as arguments to configure
AC_ARG_WITH([sasl],
  [AS_HELP_STRING([--with-sasl], [Build with SASL authentication support])],
  [WANT_SASL="$withval"],
  [WANT_SASL=check])

# Make sure --with-sasl/--without-sasl were only give yes|no|check
AS_IF([test "x$WANT_SASL" != xyes -a \
            "x$WANT_SASL" != xno -a \
            "x$WANT_SASL" != xcheck],
  [AC_MSG_ERROR([Bad value for --with-sasl: $withval])])

# If we weren't explicitly asked /not/ to test, i.e. not given --without-sasl
have_sasl=no
AS_IF([test "x$WANT_SASL" != xno],
  # Perform tests for headers and libraries. Remember, AC_CHECK_LIB
  # will give you some useful default behavior, e.g. setup LDFLAGS, if
  # you do not give it a second argument, so try not to
  [AC_CHECK_HEADER([sasl/sasl.h], , [HAVE_SASL_H=no])
   tmp_LIBS=$LIBS
   AC_CHECK_LIB([sasl2], [sasl_checkpass], , [HAVE_SASL_LIB=no])
   # Remove from LIBS, we will link it explicitly in make files.
   LIBS=$tmp_LIBS
   # If any of the tests failed
   AS_IF([test "x$HAVE_SASL_H" = xno -o \
               "x$HAVE_SASL_LIB" = xno],
     # And we were given --with, then fail
     [AS_IF([test "x$WANT_SASL" = xyes],
       [AC_MSG_ERROR([sasl requested but not available])])],
     # Otherwise, no tests failed, setup AC_SUBST/AC_DEFINE/vars for AM_CONDITIONALs
     [AC_DEFINE([BROKER_SASL_NAME], ["qpidd"],
                [The SASL app name for the qpid Broker])
      AC_DEFINE([HAVE_SASL], [1], [Enable if libsasl is present])
      have_sasl=yes])])
AM_CONDITIONAL([HAVE_SASL], [test "x$have_sasl" = xyes])


#Guess host architecture, to choose platform-dependent objects
case "$host" in
     *sun-solaris*)
        arch=solaris
     ;;
esac
AM_CONDITIONAL([SUNOS], [test x$arch = xsolaris])

# Check for some syslog capabilities not present in all systems
AC_TRY_COMPILE([#include <sys/syslog.h>], 
               [int v = LOG_AUTHPRIV;],
               [AC_DEFINE([HAVE_LOG_AUTHPRIV], [1], [Set to 1 whether LOG_AUTHPRIV is supported.])],)

AC_TRY_COMPILE([#include <sys/syslog.h>], 
               [int v = LOG_FTP;],
               [AC_DEFINE([HAVE_LOG_FTP], [1], [Set to 1 whether LOG_FTP is supported.])],)

#Check if we need to include libacl to provide acl API
gl_saved_libs=$LIBS
  AC_SEARCH_LIBS(acl, [acl],
                [test "$ac_cv_search_acl" = "none required" ||
                 LIB_ACL=$ac_cv_search_acl])
  AC_SUBST([LIB_ACL])
LIBS=$gl_saved_libs

SOCKLIBS=""
AC_CHECK_LIB([socket],[socket],[SOCKET_LIB="-lsocket"],[SOCKET_LIB=""],[])
AC_CHECK_LIB([nsl],[getipnodebyname],[NSL_LIB="-lnsl"],[NSL_LIB=""],[])
SOCKLIBS="$SOCKET_LIB $NSL_LIB"
AC_SUBST([SOCKLIBS])

AM_PATH_PYTHON()

# Files to generate	
AC_CONFIG_FILES([
  Makefile
  src/Makefile
  python/Makefile
  ruby/Makefile
  ])
AC_OUTPUT

