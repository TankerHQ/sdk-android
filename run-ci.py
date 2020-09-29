from typing import List, Optional  # noqa

import argparse
import os
import sys

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
    tanker_source: TankerSource, update: bool, tanker_ref: Optional[str]
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
        tanker_deployed_ref=tanker_ref,
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
    tanker_source: TankerSource, tanker_ref: Optional[str] = None
) -> None:
    prepare(tanker_source, False, tanker_ref)
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
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--isolate-conan-user-home",
        action="store_true",
        dest="home_isolation",
        default=False,
    )

    subparsers = parser.add_subparsers(title="subcommands", dest="command")

    reset_branch_parser = subparsers.add_parser("reset-branch")
    reset_branch_parser.add_argument("branch")

    download_artifacts_parser = subparsers.add_parser("download-artifacts")
    download_artifacts_parser.add_argument("--project-id", required=True)
    download_artifacts_parser.add_argument("--pipeline-id", required=True)
    download_artifacts_parser.add_argument("--job-name", required=True)

    build_and_test_parser = subparsers.add_parser("build-and-test")
    build_and_test_parser.add_argument(
        "--use-tanker",
        type=tankerci.conan.TankerSource,
        default=tankerci.conan.TankerSource.EDITABLE,
        dest="tanker_source",
    )
    build_and_test_parser.add_argument("--tanker-ref")

    prepare_parser = subparsers.add_parser("prepare")
    prepare_parser.add_argument(
        "--use-tanker",
        type=tankerci.conan.TankerSource,
        default=tankerci.conan.TankerSource.EDITABLE,
        dest="tanker_source",
    )
    prepare_parser.add_argument("--tanker-ref")
    prepare_parser.add_argument(
        "--update", action="store_true", default=False, dest="update",
    )

    deploy_parser = subparsers.add_parser("deploy")
    deploy_parser.add_argument("--version", required=True)
    subparsers.add_parser("mirror")

    args = parser.parse_args()
    if args.home_isolation:
        tankerci.conan.set_home_isolation()
        tankerci.conan.update_config()

    if args.command == "build-and-test":
        build_and_test(
            tanker_source=args.tanker_source, tanker_ref=args.tanker_ref,
        )
    elif args.command == "prepare":
        prepare(args.tanker_source, args.update, args.tanker_ref)
    elif args.command == "deploy":
        deploy(version=args.version)
    elif args.command == "mirror":
        tankerci.git.mirror(github_url="git@github.com:TankerHQ/sdk-android")
    elif args.command == "reset-branch":
        fallback = os.environ["CI_COMMIT_REF_NAME"]
        ref = tankerci.git.find_ref(
            Path.getcwd(), [f"origin/{args.branch}", f"origin/{fallback}"]
        )
        tankerci.git.reset(Path.getcwd(), ref)
    elif args.command == "download-artifacts":
        tankerci.gitlab.download_artifacts(
            project_id=args.project_id,
            pipeline_id=args.pipeline_id,
            job_name=args.job_name,
        )
    else:
        parser.print_help()
        sys.exit()


if __name__ == "__main__":
    main()
