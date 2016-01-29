<!-- [![Build Status](https://travis-ci.org/daisy/pipeline-webui.png?branch=master)](https://travis-ci.org/daisy/pipeline-webui) -->

# Pipeline 2 Web UI

This project provides a Web User Interface for the DAISY Pipeline 2, developed with the Play! framework.

## Publishing builds

### 1. Prepare the release

The Web UI is versioned based on git tags. If you're on the `v2.0.0` tag,
then the version used in the build will be `2.0.0`. If you're one commit
ahead of `v2.0.0`, the version will be a SNAPSHOT version on the format
`2.0.0-[commits ahead]-[commit hash]SNAPSHOT`.

If you're making a release version (not a snapshot version),
make sure that your current commit has a git tag and that
there are no changes in your project directory (staged or unstaged).

### 2. Perform the release

If you want to publish a snapshot version, simply run:

```
./activator clean universal:publish debian:publish
```

That will upload snapshot versions to sonatype.

If you want to publish a release version, you need to sign the files.
The following are instructions on how to do this manually.

<small>(We could possibly find out how to configure sbt so that it
automatically signs the files for us, but for now this is fine.)</small>

First we need to build and sign the files:

```bash
./activator clean universal:packageBin debian:debianSign
gpg -ab target/universal/*.zip
gpg -ab target/*.deb
gpg -ab target/*.pom
```

Then:

- Log into the [Sonatype Nexus Repository Manager web interface](https://oss.sonatype.org/#stagingRepositories).
- Click "Staging Upload"
- Select the upload mode "Artifact(s) with a POM"
- Using the "Select POM to Upload..." button, upload the `target/*pom` file
- One by one, using the "Select Artifact(s) to Upload..." button
  - Upload the `target/*.pom.asc` file, then click "Add Artifact"
  - Upload the `target/*.deb` file, then click "Add Artifact"
  - Upload the `target/*.deb.asc` file, then click "Add Artifact"
  - Upload the `target/universal/*.zip` file, then click "Add Artifact"
  - Upload the `target/universal/*.zip.asc` file, then click "Add Artifact"
- In the "Description" field, enter "A web-based user interface for the DAISY Pipeline 2."
- Click "Upload Artifact(s)"
- Click "Staging Repositories"
- Scroll to the bottom and click the "orgdaisy" repository that was created
- Wait for the repository to be ready; click "Refresh" until the repository can be released (Release button not disabled)
- Click the "Release" button, and "Confirm"

The Web UI is now published.

#### ...if you need permissions

You will need to have publish rights to the DAISY group on Sonatype.
Ask the [developers mailinglist](https://groups.google.com/forum/#!forum/daisy-pipeline-dev) if you don't have permissions.
Once you have permissions you need to create the file `~/.sbt/0.13/sonatype.sbt` with the following contents,
replacing with your username and password:

```
credentials += Credentials("Sonatype Nexus Repository Manager",
                           "oss.sonatype.org",
                           "<username>",
                           "<password>")
```

### 3. Prepare for the next development iteration

Merge with the `master` branch if necessary.
