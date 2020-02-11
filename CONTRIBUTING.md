# General

- Please do not use the issue tracker to ask questions.
This facility is for reporting (and fixing) bugs in the code.
If you're not 100% sure it's a bug in the code then please seek help elsewhere.
e.g. the [jenkins-users google group](https://groups.google.com/forum/#!forum/jenkinsci-users).
- [RTFM](https://en.wikipedia.org/wiki/RTFM).
The Jenkins UI pages include help that should explain things.
The [plugin's wiki page](README.md) gives additional information.
- Be helpful and make no demands.
  * This code is Free Open-Source Software - nobody is obliged to make things work for you *but* you have legal permission to fix things yourself.
  * If you're reporting and fixing an issue yourself then you only need to explain what problem you're fixing in enough detail that the maintainer(s) can understand why your changes are in the public interest.
  * If you're relying on someone else to fix a problem then you should to make it as easy as possible for others to fix it for you, and you should test any fixes provided.

# Legal conditions

- Any contributions (code, information etc) submitted will be subject to the same [license](LICENSE) as the rest of the code.
No new restrictions/conditions are permitted.
- As a contributor, you MUST have the legal right to grant permission for your contribution to be used under these conditions.

# Reporting a new issue

- Provide a full description of the issue you are facing.
  * What are you trying to do?
  * How is this failing?
  * What should happen instead?
- Provide step-by-step instructions for how to reproduce the issue.
- Specify the Jenkins core & plugin version you're seeing the issue with.
- Check and provide errors from the build log and any errors from `Manage Jenkins` -> `System Log`.
  * Exceptions and stacktraces are *especially* useful, so don't omit those...
  * Note that sensitive information may be visible in logs, so take care to redact logs where necessary.
- Ensure that any code or log examples are surrounded with [the right markdown](https://help.github.com/articles/github-flavored-markdown/) otherwise it'll be unreadable.

# Submitting pull requests

- A PR's description must EITHER refer to an existing issue (either in github or on Jenkins JIRA) OR include a full description as for "Creating new issue".
- A single PR should EITHER be making functional changes OR making (non-functional) cosmetic/refactoring changes to the code.
Please do not do both in the same PR as this makes life difficult for anyone else merging your changes.
- For functional-change PRs, keep changes to a minimum (to make merges easier).
- Clean build & test:
  * Any submitted PRs should automatically get built and tested; PRs will not be considered for merger until they are showing a clean build.
  If you believe that the build failed for reasons unconnected to your changes, close your PR, wait 10 minutes, then re-open it (just re-open the same PR; don't create a new one) to trigger a rebuild.
  Repeat until it builds clean, changing your code if necessary.
  * Please provide unit-tests for your contribution.
  * Don't give findbugs, the compiler or any IDEs anything new to complain about.

# Links

- https://plugins.jenkins.io/extra-tool-installers/
- https://jenkins.io/participate/code/
