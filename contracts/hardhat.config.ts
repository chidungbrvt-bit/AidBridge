import { HardhatUserConfig } from "hardhat/config";
import hardhatToolboxMochaEthers from "@nomicfoundation/hardhat-toolbox-mocha-ethers";

export const EXPECTED_FUJI_CHAIN_ID = 43113;
export const EXPECTED_SOLIDITY_VERSION = "0.8.28";
export const EXPECTED_EVM_VERSION = "paris";
export const EXPECTED_OPTIMIZER_RUNS = 200;

export function validateExternalNetworkEnv(env: NodeJS.ProcessEnv): void {
  if (env.REQUIRE_FUJI_CONFIG !== "true") {
    return;
  }

  const chainId = Number(env.FUJI_CHAIN_ID ?? "0");
  if (chainId !== EXPECTED_FUJI_CHAIN_ID) {
    throw new Error(
      "INVALID_CHAIN_ID: FUJI_CHAIN_ID must be exactly 43113 for Avalanche Fuji"
    );
  }

  const rpcUrl = (env.FUJI_RPC_URL ?? "").trim();
  if (!rpcUrl.startsWith("https://")) {
    throw new Error("MISSING_RPC_URL: FUJI_RPC_URL must be an HTTPS endpoint");
  }

  const adminKey = (env.FUJI_DEPLOYER_PRIVATE_KEY ?? "").trim();
  const relayerKey = (env.FUJI_RELAYER_PRIVATE_KEY ?? "").trim();
  const hexKeyPattern = /^0x[0-9a-fA-F]{64}$/;

  if (!hexKeyPattern.test(adminKey) || !hexKeyPattern.test(relayerKey)) {
    throw new Error(
      "MISSING_OR_INVALID_SECRET: Deployer and relayer private keys must be valid 32-byte hex strings"
    );
  }

  if (adminKey.toLowerCase() === relayerKey.toLowerCase()) {
    throw new Error(
      "KEY_SEPARATION_VIOLATION: Deployer admin key and organization relayer key must be distinct"
    );
  }
}

validateExternalNetworkEnv(process.env);

const config: HardhatUserConfig = {
  plugins: [hardhatToolboxMochaEthers],
  solidity: {
    version: EXPECTED_SOLIDITY_VERSION,
    settings: {
      evmVersion: EXPECTED_EVM_VERSION,
      optimizer: {
        enabled: true,
        runs: EXPECTED_OPTIMIZER_RUNS,
      },
    },
  },
};

export default config;
