package com.onemind.sales.presentation

// Planted for Guards.uiHasNoLiterals. Comments never count: Text("in a comment"), Color(0xFF000000), 12.sp

@Composable
fun SalesHeader(state: SalesState) {
    Text("Revenue")
    Text(
        "Split over lines",
    )
    Label(text = "Named literal")
    Box(Modifier.background(Color(0xFF4F46E5)))
    Text(stringResource(Res.string.sales_title), fontSize = 14.sp)
    // Compliant lookalikes:
    Text(stringResource(Res.string.sales_title))
    Text(text = state.title.asString())
    Icon(contentDescription = "Back")
    Icon(contentDescription = null)
    val url = "https://example.com/text = \"x\""
}

@Preview
@Composable
private fun SalesHeaderPreview() {
    Text("Preview sample")
    Label(text = "Preview label", fontSize = 18.sp)
    Box(Modifier.background(Color(0xFFFFFFFF)))
}

// The same body as the preview above, but not a preview: must be reported.
@Composable
private fun SalesHeaderCopy() {
    Text("Preview sample")
}

@Preview
@Composable
private fun SalesHeaderCopyPreview() {
    Text("Preview sample")
}
