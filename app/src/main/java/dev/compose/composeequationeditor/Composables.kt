package dev.compose.composeequationeditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun EquationEditor(modifier: Modifier = Modifier) {
    var currentEquation by remember { mutableStateOf<EquationElement>(EquationElement.Text("")) }
    var selectedPosition by remember { mutableStateOf<Offset?>(null) }

    Column(
        modifier = modifier.padding(16.dp)
    ) {
        // Equation display area
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.White)
                .border(1.dp, Color.Gray)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        selectedPosition = offset
                    }
                }
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val density = LocalDensity.current
                val width = with(density) { maxWidth.toPx() }
                val height = with(density) { maxHeight.toPx() }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val (w, h) = currentEquation.measure()
                    currentEquation.draw(
                        drawScope = this,
                        topLeft = Offset(
                            x = (width - w) / 2,
                            y = (height - h) / 2
                        )
                    )

                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Toolbar for equation elements
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalItemSpacing = 8.dp
        ) {
            items(equationTools) { tool ->
                Button(
                    onClick = {
                        currentEquation = when (tool) {
                            "Fraction" -> createSampleFraction()
                            "Superscript" -> createSampleSuperscript()
                            "Subscript" -> createSampleSubscript()
                            "Radical" -> createSampleRadical()
                            "Matrix" -> createSampleMatrix()
                            "Variable" -> EquationElement.Text("x")
                            "Comb" -> createSampleCombination()
                            else -> EquationElement.Text("")
                        }
                    },
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(tool)
                }
            }
        }
    }
}

private val equationTools = listOf(
    "Variable",
    "Fraction",
    "Superscript",
    "Subscript",
    "Radical",
    "Matrix",
    "Comb"
)

private fun createSampleCombination(): EquationElement {
    return EquationElement.Text("1 +  ") + EquationElement.Fraction(
        numerator = EquationElement.Text("x"),
        denominator = EquationElement.Superscript(
            base = EquationElement.Text("y"),
            power = EquationElement.Text("3"),
            isMain = false
        ),
    ) + EquationElement.Text(" +   ") + EquationElement.Fraction(
        numerator = EquationElement.Text("1"),
        denominator = EquationElement.Text("1 +  ") + EquationElement.Fraction(
            isMain = false,
            numerator = EquationElement.Text(
                "1"
            ),
            denominator = EquationElement.Text("1 +  ") + EquationElement.Radical(
                radicand = EquationElement.Text("x"),
                index = EquationElement.Text("5")
            )
        )
    )
}


private fun createSampleFraction(): EquationElement {
    return EquationElement.Fraction(
        numerator = EquationElement.Text("x + y"),
        denominator = EquationElement.Text("x - y"),
    )
}


private fun createSampleSuperscript(): EquationElement {
    return EquationElement.Superscript(
        base = EquationElement.Text("z"),
        power = EquationElement.Text("2"),
    )
}

private fun createSampleSubscript(): EquationElement {
    return EquationElement.Subscript(
        base = EquationElement.Text("x"),
        sub = EquationElement.Text("i")
    )
}

private fun createSampleRadical(): EquationElement {
    return EquationElement.Radical(
        radicand = EquationElement.Text("1+x"),
        index = EquationElement.Text("3")
    )
}

private fun createSampleMatrix(): EquationElement {
    return EquationElement.Matrix(
        rows = 2,
        columns = 2,
        elements = listOf(
            listOf(EquationElement.Text("a"), EquationElement.Text("b")),
            listOf(EquationElement.Text("c"), EquationElement.Text("d"))
        )
    )
}