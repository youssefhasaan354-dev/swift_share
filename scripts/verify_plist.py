import sys
import plistlib

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

PLIST_PATH = 'flutter_ios_companion/ios/Runner/Info.plist'

REQUIRED_ROOT_KEYS = {
    'NSLocalNetworkUsageDescription': str,
    'NSBonjourServices': list,
    'UIFileSharingEnabled': bool,
    'LSSupportsOpeningDocumentsInPlace': bool,
}

def verify():
    with open(PLIST_PATH, 'rb') as f:
        pl = plistlib.load(f)

    missing_or_invalid = []
    for key, expected_type in REQUIRED_ROOT_KEYS.items():
        if key not in pl:
            missing_or_invalid.append(f'MISSING in root: {key}')
        elif not isinstance(pl[key], expected_type):
            missing_or_invalid.append(f'INVALID TYPE in root for {key}: expected {expected_type}, got {type(pl[key])}')

    scene_manifest = pl.get('UIApplicationSceneManifest', {})
    scene_configs = scene_manifest.get('UISceneConfigurations', {})
    for role, sessions in scene_configs.items():
        if isinstance(sessions, list):
            for s in sessions:
                if isinstance(s, dict):
                    for key in REQUIRED_ROOT_KEYS:
                        if key in s:
                            missing_or_invalid.append(f'FOUND ACCIDENTALLY NESTED in {role}: {key}')

    if missing_or_invalid:
        print('[FAIL] Info.plist VERIFICATION FAILED:')
        for err in missing_or_invalid:
            print(f'  - {err}')
        sys.exit(1)

    print('[PASS] Info.plist VERIFICATION PASSED: All required network & file sharing keys exist in root dict.')
    for key in REQUIRED_ROOT_KEYS:
        print(f'  - {key} = {pl[key]}')

if __name__ == '__main__':
    verify()
