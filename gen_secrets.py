#!/usr/bin/env python3
"""
Generate RSA key pairs for JWT signing.

This script generates RSA private/public key pairs in the locations required
by the Quarkus application for JWT authentication. The keys are used for:
- Signing JWTs at login
- Verifying JWTs on protected endpoints

Usage:
    python gen_secrets.py [--force]

Options:
    --force    Overwrite existing key files

Key locations:
    - thunder/engine/provider/src/main/resources/
    - thunder/engine/provider/src/test/resources/
"""

import os
import sys
import subprocess
from pathlib import Path


# Key locations relative to project root
KEY_LOCATIONS = [
    "thunder/engine/provider/src/main/resources",
    "thunder/engine/provider/src/test/resources",
]

PRIVATE_KEY_NAME = "privateKey.pem"
PUBLIC_KEY_NAME = "publicKey.pem"


def find_project_root() -> Path:
    """Find the project root directory."""
    current = Path(__file__).resolve().parent
    while current != current.parent:
        if (current / "pom.xml").exists():
            return current
        current = current.parent
    # Fallback to script location
    return Path(__file__).resolve().parent


def generate_keys(output_dir: Path, force: bool = False) -> bool:
    """
    Generate RSA key pair in the specified directory.

    Args:
        output_dir: Directory to write keys to
        force: If True, overwrite existing keys

    Returns:
        True if keys were generated, False if skipped
    """
    private_key_path = output_dir / PRIVATE_KEY_NAME
    public_key_path = output_dir / PUBLIC_KEY_NAME

    # Check if keys already exist
    if private_key_path.exists() and public_key_path.exists() and not force:
        print(f"  Keys already exist in {output_dir}")
        return False

    # Ensure output directory exists
    output_dir.mkdir(parents=True, exist_ok=True)

    try:
        # Generate private key in PKCS#8 format (required by smallrye-jwt)
        # Using 2048-bit RSA for compatibility
        print(f"  Generating private key: {private_key_path}")
        subprocess.run(
            [
                "openssl", "genpkey",
                "-algorithm", "RSA",
                "-out", str(private_key_path),
                "-pkeyopt", "rsa_keygen_bits:2048"
            ],
            check=True,
            capture_output=True
        )

        # Extract public key
        print(f"  Generating public key: {public_key_path}")
        subprocess.run(
            [
                "openssl", "rsa",
                "-pubout",
                "-in", str(private_key_path),
                "-out", str(public_key_path)
            ],
            check=True,
            capture_output=True
        )

        print(f"  Keys generated successfully in {output_dir}")
        return True

    except subprocess.CalledProcessError as e:
        print(f"  ERROR: Failed to generate keys: {e}")
        if e.stderr:
            print(f"  {e.stderr.decode()}")
        return False
    except FileNotFoundError:
        print("  ERROR: 'openssl' command not found. Please install OpenSSL.")
        return False


def main():
    """Main entry point."""
    force = "--force" in sys.argv

    print("JWT Key Generator for Thunder Engine")
    print("=" * 40)

    project_root = find_project_root()
    print(f"Project root: {project_root}")
    print()

    success_count = 0
    for location in KEY_LOCATIONS:
        key_dir = project_root / location
        print(f"Processing: {location}")
        if generate_keys(key_dir, force):
            success_count += 1
        print()

    print("=" * 40)
    if success_count > 0:
        print(f"Generated keys in {success_count} location(s)")
    else:
        print("No new keys generated (use --force to regenerate)")

    # Verify all key locations have keys
    all_present = True
    for location in KEY_LOCATIONS:
        key_dir = project_root / location
        private_key = key_dir / PRIVATE_KEY_NAME
        public_key = key_dir / PUBLIC_KEY_NAME
        if not (private_key.exists() and public_key.exists()):
            print(f"WARNING: Missing keys in {location}")
            all_present = False

    if all_present:
        print("All key locations have valid keys.")
        return 0
    else:
        print("Some key locations are missing keys!")
        return 1


if __name__ == "__main__":
    sys.exit(main())
