{
  description = "Trimmer Flake";

  inputs = {
    nixpkgs = {
      url = github:NixOS/nixpkgs/release-20.09;
    };

    flake-utils = {
      url = "github:numtide/flake-utils";
    };
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem
      (system:
        with nixpkgs.legacyPackages.${system};
        let

          deps = stdenv.mkDerivation {
            name = "deps";
            src = ./.;
            nativeBuildInputs = [ gradle perl ];
            buildPhase = ''
              export GRADLE_USER_HOME=$(mktemp -d)
              gradle --no-daemon build
            '';
            # perl code mavenizes pathes (com.squareup.okio/okio/1.13.0/a9283170b7305c8d92d25aff02a6ab7e45d06cbe/okio-1.13.0.jar -> com/squareup/okio/okio/1.13.0/okio-1.13.0.jar)
            installPhase = ''
              find $GRADLE_USER_HOME/caches/modules-2 -type f -regex '.*\.\(jar\|pom\)' \
                | perl -pe 's#(.*/([^/]+)/([^/]+)/([^/]+)/[0-9a-f]{30,40}/([^/\s]+))$# ($x = $2) =~ tr|\.|/|; "install -Dm444 $1 \$out/$x/$3/$4/$5" #e' \
                | sh
            '';
            outputHashAlgo = "sha256";
            outputHashMode = "recursive";
            outputHash = "sha256-7LBcsAvh2sGFJ2Pkehu+l7RYsf87tlJ5UbQBhi9rl64=";
          };
        in
        rec {
          defaultApp = {
            type = "app";
            program = "${defaultPackage}/bin/trimmer";
          };

          defaultPackage = stdenv.mkDerivation {
            pname = "trimmer";
            version = "1.0";
            src = ./.;
            nativeBuildInputs = [ gradle makeWrapper ];

            buildPhase = ''
              export GRADLE_USER_HOME=$(mktemp -d)

              # point to offline repo
              echo '${deps}'
              sed -i 's#maven.*#maven { url = uri("${deps}") }#' build.gradle.kts
              sed -i 's#gradlePluginPortal.*#maven { url = uri("${deps}") }#' settings.gradle

              gradle --offline --no-daemon distTar
            '';

            installPhase = ''
              mkdir $out
              tar xvf build/distributions/trimmer-1.0.tar --directory=$out --strip=1
              wrapProgram $out/bin/trimmer \
                --set JAVA_HOME ${jre_headless}
            '';
          };

          devShell = pkgs.mkShell {
            buildInputs = [
              gradle
            ];
          };
        }
      );
}
