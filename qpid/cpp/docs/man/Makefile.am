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

# Generate makefile from qpidd --help
#
# Note: qiddd.1 is normally a _checked in_ pre-generated file, so that
# make dist does not have to build the entire source just for the man page.
#
# To update the checked-in file (e.g. for a new release) do the following:
#
# - start with a completely clean checkout.
# - make sure there are no modules installed in your configured prefix,
#   we don't want to pick up configuration from optional modules
# - do bootstrap; configure
# - in build-dir: cd src; make # build the broker
# - in source-dir: cd docs/man; rm qpidd.1 # remove checked-in man page.
# - in build-dir: cd docs/man; make # make new man page
# - edit qpidd.1 to remove all default values referring to file/directory locations.
#   these values will differ between builds depending on configuration.
# - if source-dir != build-dir: copy qpidd.1 from build-dir/docs/man to source-dir/docs/man

dist_man_MANS = qpidd.1

man_aux = $(dist_man_MANS:.1=.x)
EXTRA_DIST = $(man_aux) generate_manpage groffify_options.sed groffify_template.sed
DISTCLEANFILES = $(dist_man_MANS)
CLEANFILES=qpidd.1

dist-hook: $(man_aux)

# Depend on configure.ac to get version number changes.
$(dist_man_MANS): $(top_srcdir)/configure.ac

if HAVE_HELP2MAN

SUFFIXES = .x .1
qpidd.1: $(srcdir)/qpidd.x $(top_builddir)/src/qpidd
.x.1:
	@rm -f $@
	@echo "Updating man page $@"
	$(HELP2MAN) --no-info --include=$(srcdir)/$*.x --output=$@-t --help-option="--help --no-module-dir" --version-option="--version --no-module-dir" ../../src/$*
	@chmod a-w $@-t
	@mv $@-t $@
else
qpidd.1:
	@echo "Warning: help2man not available, using sed script instead."
	$(srcdir)/generate_manpage $(srcdir)/qpidd.x $(top_builddir)/src/qpidd $@
endif


