import argparse
import os
import shutil
import sys
from pathlib import Path
from typing import Optional  # noqa

import cli_ui as ui
import tankerci
import tankerci.android
import tankerci.conan
import tankerci.cpp
import tankerci.gcp
import tankerci.git
import tankerci.gitlab
from tankerci.conan import Profile, TankerSource

PROFILES = [
    Profile(["linux-x86_64", "shared"]),
    Profile("android-x86"),
    Profile("android-x86_64"),
    Profile("android-armv7"),
    Profile("android-armv8"),
]

LATEST_STABLE_REF = "tanker/latest-stable@"

def parse_profile(profile: str) -> Profile:
    if profile.endswith("-shared"):
        return Profile([profile[:-len("-shared")], "shared"])
    return Profile(profile)


def prepare(
    tanker_source: TankerSource, update: bool, tanker_ref: Optional[str]
) -> None:
    artifact_path = Path.cwd() / "package"
    tanker_deployed_ref = tanker_ref

    if tanker_source == TankerSource.UPSTREAM:
        profiles = [parse_profile(d.name) for d in artifact_path.iterdir() if d.is_dir()]
    else:
        profiles = PROFILES
    if tanker_source == TankerSource.DEPLOYED and not tanker_deployed_ref:
        tanker_deployed_ref = "tanker/latest-stable@"
    tankerci.conan.install_tanker_source(
        tanker_source,
        output_path=Path("tanker-bindings/conan"),
        host_profiles=profiles,
        build_profile=Profile("linux-x86_64"),
        update=update,
        tanker_deployed_ref=tanker_deployed_ref,
    )


def build() -> None:
    tankerci.run("./gradlew", "tanker-bindings:buildNativeRelease")
    tankerci.run("./gradlew", "tanker-bindings:assembleRelease")

    dest_path = Path.cwd() / "artifacts"
    shutil.rmtree(dest_path, ignore_errors=True)
    dest_path.mkdir(parents=True)
    shutil.copy(
        Path.cwd() / "tanker-bindings/build/outputs/aar/tanker-bindings-release.aar",
        dest_path / "tanker-bindings.aar",
    )


def dump_logcat_for_failed_tests() -> None:
    try:
        dump_path = "tanker-bindings/build/reports/androidTests/connected/flavors/releaseAndroidTest/logcat.txt"
        tankerci.android.dump_logcat(dump_path)
        ui.info("Tests have failed, logcat dumped to", dump_path)
    except Exception as e:
        ui.error("Failed to dump logcat:", e)


def test() -> None:
    ui.info_1("Building tests")
    tankerci.run("./gradlew", "packageReleaseAndroidTest", "-PandroidTestRelease")
    ui.info_1("Running tests")
    try:
        with tankerci.android.emulator():
            try:
                tankerci.run(
                    "./gradlew", "connectedAndroidTest", "-PandroidTestRelease"
                )
            except:
                dump_logcat_for_failed_tests()
                tankerci.android.take_screenshot(Path.cwd() / "screenshot.png")
                raise
    finally:
        shutil.copytree(
            "tanker-bindings/build/reports", Path.cwd() / "artifacts/reports"
        )


def build_and_test(
    tanker_source: TankerSource, tanker_ref: Optional[str] = None
) -> None:
    prepare(tanker_source, False, tanker_ref)
    build()
    test()


def deploy(*, version: str, tanker_ref: str) -> None:
    tankerci.bump_files(version)
    prepare(TankerSource.DEPLOYED, False, tanker_ref)
    build()

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
    parser.add_argument("--remote", default="artifactory")

    subparsers = parser.add_subparsers(title="subcommands", dest="command")

    reset_branch_parser = subparsers.add_parser("reset-branch")
    reset_branch_parser.add_argument("branch", nargs="?")

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

    build_parser = subparsers.add_parser("build")
    build_parser.add_argument(
        "--use-tanker",
        type=tankerci.conan.TankerSource,
        default=tankerci.conan.TankerSource.EDITABLE,
        dest="tanker_source",
    )
    build_parser.add_argument("--tanker-ref")

    prepare_parser = subparsers.add_parser("prepare")
    prepare_parser.add_argument(
        "--use-tanker",
        type=tankerci.conan.TankerSource,
        default=tankerci.conan.TankerSource.EDITABLE,
        dest="tanker_source",
    )
    prepare_parser.add_argument("--tanker-ref")
    prepare_parser.add_argument(
        "--update",
        action="store_true",
        default=False,
        dest="update",
    )

    deploy_parser = subparsers.add_parser("deploy")
    deploy_parser.add_argument("--version", required=True)
    deploy_parser.add_argument("--tanker-ref", required=True)

    args = parser.parse_args()
    command = args.command

    user_home = None
    if args.home_isolation:
        user_home = Path.cwd() / ".cache" / "conan" / args.remote
        if command in ("build-and-test", "deploy"):
            # Because of GitLab issue https://gitlab.com/gitlab-org/gitlab/-/issues/254323
            # the downstream deploy jobs will be triggered even if upstream has failed
            # By removing the cache we ensure that we do not use a
            # previously built (and potentially broken) release candidate to deploy a binding
            tankerci.conan.run("remove", "tanker/*", "--force")

    if command == "build-and-test":
        with tankerci.conan.ConanContextManager([args.remote], conan_home=user_home):
            build_and_test(
                tanker_source=args.tanker_source,
                tanker_ref=args.tanker_ref,
            )
    elif command == "build":
        with tankerci.conan.ConanContextManager([args.remote], conan_home=user_home):
            prepare(args.tanker_source, False, args.tanker_ref)
            build()
    elif command == "prepare":
        with tankerci.conan.ConanContextManager([args.remote], conan_home=user_home):
            prepare(args.tanker_source, args.update, args.tanker_ref)
    elif command == "deploy":
        with tankerci.conan.ConanContextManager([args.remote], conan_home=user_home, clean_on_exit=True):
            deploy(version=args.version, tanker_ref=args.tanker_ref)
    elif command == "reset-branch":
        fallback = os.environ["CI_COMMIT_REF_NAME"]
        ref = tankerci.git.find_ref(
            Path.cwd(), [f"origin/{args.branch}", f"origin/{fallback}"]
        )
        tankerci.git.reset(Path.cwd(), ref, clean=False)
    elif command == "download-artifacts":
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
