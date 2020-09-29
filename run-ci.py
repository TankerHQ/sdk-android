from typing import List, Optional  # noqa

from path import Path
import cli_ui as ui

import tankerci
import tankerci.conan
from tankerci.conan import TankerSource
import tankerci.cpp
import tankerci.git
import tankerci.gcp
import tankerci.gitlab


PROFILES = [
    "gcc8-release-shared",
    "android-x86-release",
    "android-x86_64-release",
    "android-armv7-release",
    "android-armv8-release",
]


def prepare(
    tanker_source: TankerSource, update: bool, tanker_deployed_ref: Optional[str]
) -> None:
    artifact_path = Path.getcwd() / "package"
    if tanker_source == TankerSource.UPSTREAM:
        profiles = [d.basename() for d in artifact_path.dirs()]
    else:
        profiles = PROFILES
    tankerci.conan.install_tanker_source(
        tanker_source,
        output_path=Path("tanker-bindings/conan"),
        profiles=profiles,
        update=update,
        tanker_deployed_ref=tanker_deployed_ref,
    )


def build() -> None:
    tankerci.run("./gradlew", "tanker-bindings:buildNativeRelease")
    tankerci.run("./gradlew", "tanker-bindings:assembleRelease")


def test() -> None:
    ui.info_1("Running tests")
    tankerci.run(
        "./gradlew", "tanker-bindings:testRelease",
    )


def build_and_test(
    tanker_source: TankerSource, tanker_deployed_ref: Optional[str] = None
) -> None:
    prepare(tanker_source, False, tanker_deployed_ref)
    build()
    test()


def deploy(*, version: str) -> None:
    tankerci.bump_files(version)
    prepare(TankerSource.DEPLOYED, False, None)
    build()
    test()

    ui.info_1("Deploying SDK to https://storage.googleapis.com/maven.tanker.io")
    tankerci.gcp.GcpProject("tanker-prod").auth()
    tankerci.run("./gradlew", "tanker-bindings:publish")


def main():
    parser = tankerci.cpp.init_parser()

    args = parser.parse_args()
    if args.home_isolation:
        tankerci.conan.set_home_isolation()

    if args.command == "build-and-test":
        build_and_test(
            tanker_source=args.tanker_source,
            tanker_deployed_ref=args.tanker_deployed_ref,
        )
    elif args.command == "prepare":
        prepare(args.tanker_source, args.update, args.tanker_deployed_ref)
    elif args.command == "deploy":
        deploy(version=args.version)
    elif args.command == "mirror":
        tankerci.git.mirror(github_url="git@github.com:TankerHQ/sdk-ios")
    else:
        tankerci.cpp.handle_common_subcommands(parser, args)


if __name__ == "__main__":
    main()
