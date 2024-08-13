# CLEAR ID Verification

The CLEAR ID Verification node lets administrators integrate CLEAR's hosted UI for verification inside a Journey.

## Compatibility

<table>
  <colgroup>
    <col>
    <col>
  </colgroup>
  <thead>
  <tr>
    <th>Product</th>
    <th>Compatible?</th>
  </tr>
  </thead>
  <tbody>
  <tr>
    <td><p>ForgeRock Identity Cloud</p></td>
    <td><p><span>Yes</span></p></td>
  </tr>
  <tr>
    <td><p>ForgeRock Access Management (self-managed)</p></td>
    <td><p><span>Yes</span></p></td>
  </tr>
  <tr>
    <td><p>ForgeRock Identity Platform (self-managed)</p></td>
    <td><p><span>Yes</span></p></td>
  </tr>
  </tbody>
</table>

## Inputs

Everything this node needs is configured within the node.

## Configuration

<table>
  <thead>
  <th>Property</th>
  <th>Usage</th>
  </thead>

  <tr>
    <td>CLEAR API Key</td>
      <td>Environment API Key for CLEAR.
      </td>
  </tr>
  <tr>
    <td>Project ID</td>
    <td>The Project ID for the desired CLEAR project.
    </td>

  </tr>
  <tr>
    <td>Redirect URL</td>
    <td>The target URL for the post-verification redirect.
    </td>
  </tr>
  <tr>
    <td>Use Secure Endpoint</td>
    <td>If the toggle is enabled, the Secure Endpoint will be used to retrieves user verification results; otherwise, the Standard Endpoint is used.
    </td>
  </tr>
</table>

## Outputs

This node retrieves the user's verification results and stores them in transient state.

## Outcomes

`Continue`

Successfully verified and redirected the user.

`Error`

There was an error during the verification process.

## Troubleshooting

If this node logs an error, review the log messages to find the reason for the error and address the issue appropriately.