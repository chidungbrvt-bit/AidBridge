// SPDX-License-Identifier: MIT
pragma solidity 0.8.28;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

/**
 * @title ToolchainSmokeProbe
 * @notice Minimal C02 local toolchain smoke probe to verify Solidity 0.8.28,
 *         Paris EVM compilation, OpenZeppelin AccessControl wiring, and
 *         distinct admin / relayer / unauthorized test accounts.
 *         Not the C03 AidRegistry implementation.
 */
contract ToolchainSmokeProbe is AccessControl {
    bytes32 public constant RELAYER_ROLE = keccak256("RELAYER_ROLE");

    error ZeroAddressNotAllowed();
    error AdminRelayerMustBeDistinct();

    event SmokeCheckRecorded(address indexed caller, uint64 indexed chainId);

    constructor(address admin, address relayer) {
        if (admin == address(0) || relayer == address(0)) {
            revert ZeroAddressNotAllowed();
        }
        if (admin == relayer) {
            revert AdminRelayerMustBeDistinct();
        }
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(RELAYER_ROLE, relayer);
    }

    function recordSmokeCheck() external onlyRole(RELAYER_ROLE) returns (uint64) {
        uint64 currentChainId = uint64(block.chainid);
        emit SmokeCheckRecorded(msg.sender, currentChainId);
        return currentChainId;
    }
}
