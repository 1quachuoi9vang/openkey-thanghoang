{ pkgs, ... }: {
  channel = "stable-24.05";

  packages = [
    pkgs.jdk21
    pkgs.python312
    pkgs.android-tools
  ];

  env = {
    JAVA_HOME = "${pkgs.jdk21}";
  };

  idx = {
    extensions = [
      "fwcd.kotlin"
      "mathiasfrohlich.Kotlin"
    ];

    workspace = {
      onCreate = {
        verify-and-build = "python3 scripts/sync_shared_lexicon.py --check && ./gradlew :openkey-core:test :app:assembleDebug --no-daemon";
      };
      onStart = {
        check-lexicon = "python3 scripts/sync_shared_lexicon.py --check";
      };
    };

    previews = {
      enable = true;
      previews = {
        android = {
          manager = "android";
        };
      };
    };
  };
}
