import argparse
import sys
import os
import contextlib
import shutil

from path import Path
import cli_ui as ui

import ci
import ci.conan
import ci.cpp
import ci.git
import ci.gcp


def build(*, native_from_sources: bool) -> None:
    ui.info_1("build everything")
    if native_from_sources:
        ci.run("./gradlew", "tanker-bindings:buildNativeRelease")
    else:
        ci.run("./gradlew", "tanker-bindings:useDeployedNativeRelease")
    ci.run("./gradlew", "tanker-bindings:assembleRelease")


def test() -> None:
    ui.info_1("Running tests")
    config_path = ci.tanker_configs.get_path()
    ci.run(
        "./gradlew",
        "tanker-bindings:testRelease",
        f"-DTANKER_CONFIG_FILEPATH={config_path}",
        "-DTANKER_CONFIG_NAME=dev",
    )


def build_and_test(args) -> None:
    native_from_sources = False
    android_path = Path.getcwd()
    if args.use_tanker == "deployed":
        native_from_sources = False
    elif args.use_tanker == "same-as-branch":
        workspace = ci.git.prepare_sources(repos=["sdk-native", "sdk-android"])
        android_path = workspace / "sdk-android"
        ci.conan.export(src_path=workspace / "sdk-native", ref_or_channel="tanker/dev")
        native_from_sources = True
    elif args.use_tanker == "local":
        native_from_sources = True
        ci.conan.export(
            src_path=Path.getcwd().parent / "sdk-native", ref_or_channel="tanker/dev"
        )

    with android_path:
        build(native_from_sources=native_from_sources)
        test()
    shutil.copytree(android_path / "tanker-bindings/build/reports/tests", Path.getcwd() / "tests_report")


def deploy(*, git_tag: str) -> None:
    version = ci.version_from_git_tag(git_tag)
    ci.bump_files(version)
    build(native_from_sources=False)
    test()

    ui.info_1("Deploying SDK to maven.tanker.io")
    ci.gcp.GcpProject("tanker-prod").auth()
    ci.run("./gradlew", "tanker-bindings:publish")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--isolate-conan-user-home",
        action="store_true",
        dest="home_isolation",
        default=False,
    )
    subparsers = parser.add_subparsers(title="subcommands", dest="command")

    update_conan_config_parser = subparsers.add_parser("update-conan-config")

    check_parser = subparsers.add_parser("build-and-test")
    check_parser.add_argument(
        "--use-tanker", choices=["local", "deployed", "same-as-branch"], default="local"
    )

    deploy_parser = subparsers.add_parser("deploy")
    deploy_parser.add_argument("--git-tag", required=True)

    subparsers.add_parser("mirror")

    args = parser.parse_args()
    if args.home_isolation:
        ci.conan.set_home_isolation()

    if args.command == "update-conan-config":
        ci.cpp.update_conan_config()
    elif args.command == "build-and-test":
        build_and_test(args)
    elif args.command == "deploy":
        deploy(git_tag=args.git_tag)
    elif args.command == "mirror":
        ci.git.mirror(github_url="git@github.com:TankerHQ/sdk-android")
    else:
        parser.print_help()
        sys.exit(1)


if __name__ == "__main__":
    main()
