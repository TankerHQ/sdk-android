import argparse
import sys
import shutil

from path import Path
import cli_ui as ui

import tankerci
import tankerci.conan
import tankerci.cpp
import tankerci.git
import tankerci.gcp
import tankerci.gitlab


def build(*, use_tanker: str) -> None:
    ui.info_1("build everything")
    if use_tanker in ["same-as-branch", "local"]:
        tankerci.run("./gradlew", "tanker-bindings:buildNativeRelease")
    elif use_tanker == "upstream":
        tankerci.run("./gradlew", "tanker-bindings:useUpstreamNativeRelease")
    else:
        tankerci.run("./gradlew", "tanker-bindings:useDeployedNativeRelease")
    tankerci.run("./gradlew", "tanker-bindings:assembleRelease")


def test() -> None:
    ui.info_1("Running tests")
    tankerci.run(
        "./gradlew", "tanker-bindings:testRelease",
    )


def build_and_test(args) -> None:
    cwd = Path.getcwd()
    android_path = cwd
    if args.use_tanker == "same-as-branch":
        workspace = tankerci.git.prepare_sources(repos=["sdk-native", "sdk-android"])
        android_path = workspace / "sdk-android"
        tankerci.conan.export(
            src_path=workspace / "sdk-native", ref_or_channel="tanker/dev@"
        )
    elif args.use_tanker == "upstream":
        artifacts_path = Path.getcwd() / "package"
        profiles = [d.basename() for d in artifacts_path.dirs()]
        for profile in profiles:
            package_folder = artifacts_path / profile

            tankerci.conan.export_pkg(
                artifacts_path / "conanfile.py",
                profile=profile,
                force=True,
                package_folder=package_folder,
            )
    elif args.use_tanker == "local":
        tankerci.conan.export(
            src_path=Path.getcwd().parent / "sdk-native", ref_or_channel="tanker/dev@"
        )

    with android_path:
        build(use_tanker=args.use_tanker)
        test()
    Path(android_path / "tanker-bindings/build/reports/tests").copytree(
        cwd / "tests_report"
    )


def deploy(*, git_tag: str) -> None:
    version = tankerci.version_from_git_tag(git_tag)
    tankerci.bump_files(version)
    build(native_from_sources=False)
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

    check_parser = subparsers.add_parser("build-and-test")
    check_parser.add_argument(
        "--use-tanker", choices=["local", "deployed", "same-as-branch", "upstream"], default="local"
    )

    deploy_parser = subparsers.add_parser("deploy")
    deploy_parser.add_argument("--git-tag", required=True)

    subparsers.add_parser("mirror")

    args = parser.parse_args()
    if args.home_isolation:
        tankerci.conan.set_home_isolation()
        tankerci.conan.update_config()

    if args.command == "build-and-test":
        build_and_test(args)
    elif args.command == "deploy":
        deploy(git_tag=args.git_tag)
    elif args.command == "reset-branch":
        ref = tankerci.git.find_ref(
            Path.getcwd(), [f"origin/{args.branch}", "origin/master"]
        )
        tankerci.git.reset(Path.getcwd(), ref)
    elif args.command == "download-artifacts":
        tankerci.gitlab.download_artifacts(
            project_id=args.project_id,
            pipeline_id=args.pipeline_id,
            job_name=args.job_name,
        )
    elif args.command == "mirror":
        tankerci.git.mirror(github_url="git@github.com:TankerHQ/sdk-android")
    else:
        parser.print_help()
        sys.exit(1)


if __name__ == "__main__":
    main()
