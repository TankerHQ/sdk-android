import argparse
import os
import sys

from enum import Enum
from path import Path
import cli_ui as ui

import tankerci
import tankerci.conan
import tankerci.cpp
import tankerci.git
import tankerci.gcp
import tankerci.gitlab

class TankerSource(Enum):
    LOCAL = "local"
    SAME_AS_BRANCH = "same-as-branch"
    DEPLOYED = "deployed"
    UPSTREAM = "upstream"

def generate_conanfile(android_path: Path, conan_reference: str) -> None:
    in_path = android_path / "tanker-bindings" / "conan" / "conanfile.in.txt"
    out_path = in_path.parent / "conanfile.txt"
    contents = in_path.text()
    contents = contents.replace("@SDK_NATIVE_REFERENCE@", conan_reference)
    out_path.write_text(contents)
    ui.info_2("Generated", out_path)


def retrieve_conan_reference(*, recipe_dir: Path) -> str:
    recipe_info = tankerci.conan.inspect(recipe_dir)
    name = recipe_info["name"]
    version = recipe_info["version"]
    return f"{name}/{version}@"


def build(*, use_tanker: TankerSource) -> None:
    ui.info_1("build everything")
    if use_tanker in [TankerSource.SAME_AS_BRANCH, TankerSource.LOCAL]:
        tankerci.run("./gradlew", "tanker-bindings:buildNativeRelease")
    elif use_tanker == TankerSource.UPSTREAM:
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
    if args.tanker_source == TankerSource.SAME_AS_BRANCH:
        workspace = tankerci.git.prepare_sources(repos=["sdk-native", "sdk-android"])
        android_path = workspace / "sdk-android"
        recipe_dir = workspace / "sdk-native"
        tankerci.conan.export(src_path=recipe_dir)
        conan_reference = retrieve_conan_reference(recipe_dir=recipe_dir)
    elif args.use_tanker == TankerSource.DEPLOYED:
        conan_reference = os.environ["SDK_NATIVE_LATEST_CONAN_REFERENCE"]
    elif args.use_tanker == TankerSource.UPSTREAM:
        artifacts_path = cwd / "package"
        recipe_dir = artifacts_path
        conan_reference = retrieve_conan_reference(recipe_dir=recipe_dir)
        profiles = [d.basename() for d in artifacts_path.dirs()]
        for profile in profiles:
            package_folder = artifacts_path / profile

            tankerci.conan.export_pkg(
                artifacts_path / "conanfile.py",
                profile=profile,
                force=True,
                package_folder=package_folder,
            )
    elif args.use_tanker == TankerSource.Local:
        recipe_dir = cwd.parent / "sdk-native"
        conan_reference = retrieve_conan_reference(recipe_dir=recipe_dir)
        tankerci.conan.export(src_path=recipe_dir)

    generate_conanfile(android_path, conan_reference)
    with android_path:
        build(use_tanker=args.tanker_source)
        test()
    Path(android_path / "tanker-bindings/build/reports/tests").copytree(
        cwd / "tests_report"
    )


def deploy(*, version: str) -> None:
    tankerci.bump_files(version)
    conan_reference = os.environ["SDK_NATIVE_LATEST_CONAN_REFERENCE"]
    generate_conanfile(Path.getcwd(), conan_reference)
    build(use_tanker="deployed")
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
        "--use-tanker",
        type=TankerSource,
        default=TankerSource.LOCAL,
        dest="tanker_source",
    )

    deploy_parser = subparsers.add_parser("deploy")
    deploy_parser.add_argument("--version", required=True)

    subparsers.add_parser("mirror")

    args = parser.parse_args()
    if args.home_isolation:
        tankerci.conan.set_home_isolation()
        tankerci.conan.update_config()

    if args.command == "build-and-test":
        build_and_test(args)
    elif args.command == "deploy":
        deploy(version=args.version)
    elif args.command == "reset-branch":
        fallback = os.environ["CI_COMMIT_REF_NAME"]
        ref = tankerci.git.find_ref(
            Path.getcwd(),
            [f"origin/{args.branch}", f"origin/{fallback}"]
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
