# Version History

### Unreleased
CI build of latest code can be found [here](https://ci.jenkins.io/job/Plugins/job/extra-tool-installers-plugin/job/master/lastSuccessfulBuild/artifact/target/).
* ![(plus)](docs/images/add.svg)
Added "Find on PATH" installer.

### Version 1.0
_Aug 10, 2020_
* ![(info)](docs/images/information.svg)
Moved documentation from (deprecated) Jenkins Wiki to github.
* ![(plus)](docs/images/add.svg)
Tidied up documentation so it's all using the same Jenkins style in screenshots.

### Version 0.5
_Feb 08, 2019_
* ![(plus)](docs/images/add.svg)
Added the "Download (with basic authentication) and extract .zip/.tar.gz" installer.
* ![(plus)](docs/images/add.svg)
Added the "Try any of ..." installer.
* ![(info)](docs/images/information.svg)
Update the core requirement to 2.60.3.

### Version 0.4
_Dec 12, 2017_
* ![(info)](docs/images/information.svg)
Update the core requirement to 1.651.3.
* ![(error)](docs/images/error.svg)
Cleanup the documentation and minor issues reported by FindBugs.

### Version 0.3
_Jan 19, 2014_
* ![(error)](docs/images/error.svg)
Added a home directory to the stub installer to avoid validation failures => warnings support
([JENKINS-19527](https://issues.jenkins-ci.org/browse/JENKINS-19527)).
* ![(plus)](docs/images/add.svg)
Parameters substitution error check is optional (false by default)
* ![(info)](docs/images/information.svg)
Renamed BatchCommandInstaller to avoid display name conflicts
([JENKINS-21202](https://issues.jenkins-ci.org/browse/JENKINS-21202)).

### Version 0.2
_Aug 16, 2013_
* ![(plus)](docs/images/add.svg)
Stub installer: Print warning message or fail the build
* ![(plus)](docs/images/add.svg)
Support of variables substitution in the "Tool Home" and stub installer's "Message"

### Version 0.1
_Jul 20, 2013_
* ![(plus)](docs/images/add.svg)
Batch Command Installer
* ![(plus)](docs/images/add.svg)
Shared Directory Installer ("install from the specified folder")
