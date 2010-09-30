#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

if HAVE_PYTHON_DEVEL

INCLUDES = -I$(top_srcdir)/include -I$(top_builddir)/include -I$(top_srcdir)/src -I$(top_builddir)/src $(QMF_INCLUDES)

generated_file_list = \
	cqmf2.cpp \
	cqmf2.py

EXTRA_DIST = python.i
BUILT_SOURCES = $(generated_file_list)

$(generated_file_list): $(srcdir)/python.i $(srcdir)/../qmf2.i $(srcdir)/../../swig_python_typemaps.i
	swig -c++ -python -Wall $(INCLUDES) $(QPID_CXXFLAGS) -I/usr/include -o cqmf2.cpp $(srcdir)/python.i

pylibdir = $(PYTHON_LIB)

lib_LTLIBRARIES = _cqmf2.la

_cqmf2_la_LDFLAGS = -avoid-version -module -shared
_cqmf2_la_LIBADD = $(PYTHON_LIBS) -L$(top_builddir)/src/.libs $(top_builddir)/src/libqmf2.la
_cqmf2_la_CXXFLAGS = $(INCLUDES) -I$(srcdir)/qmf -I$(PYTHON_INC)
nodist__cqmf2_la_SOURCES = cqmf2.cpp

CLEANFILES = $(generated_file_list)

endif  # HAVE_PYTHON_DEVEL
