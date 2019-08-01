import argparse
import sys
import os
import contextlib

from path import Path
import cli_ui as ui

import ci
import ci.android
import ci.conan
import ci.cpp
import ci.git
import ci.gcp


def build(*, native_from_sources: bool) -> None:
    ui.info_1("build everything")
    if native_from_sources:
        ci.android.run_gradle("tanker-bindings:buildNativeRelease")
    else:
        ci.android.run_gradle("tanker-bindings:useDeployedNativeRelease")
    env = os.environ.copy()
    ci.android.run_gradle("tanker-bindings:assembleRelease", env=env)


def test() -> None:
    ui.info_1("Running tests")
    config_path = ci.tanker_configs.get_path()
    ci.android.run_gradle(
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


def deploy(*, git_tag: str) -> None:
    version = ci.version_from_git_tag(git_tag)
    ci.bump_files(version)
    build(native_from_sources=False)
    test()
    ui.info_1("Deploying SDK to maven.tanker.io")
    ci.android.run_gradle("tanker-bindings:assembleRelease")
    ci.gcp.GcpProject("tanker-prod").auth()

    # Note: we need to downolad the *entire*
    # bucket, otherwise the file:// maven deployer
    # does not work.
    # FIXME: use the GCS wagon plugin instead:
    #  https://github.com/drcrallen/gswagon-maven-plugin
    ci.android.bucket_download()
    ci.android.run_gradle(
        "tanker-bindings:uploadArchive",
        "-P",
        "artifactsPath=%s" % ci.android.TANKER_ARTIFACTS_PATH,
    )
    ci.android.bucket_upload()


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
