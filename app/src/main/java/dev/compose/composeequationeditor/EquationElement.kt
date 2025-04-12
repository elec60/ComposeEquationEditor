package dev.compose.composeequationeditor

import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.max

/**
 * Base sealed class for all equation elements.
 * Follows the composite design pattern to build complex equations from simple elements.
 */
sealed class EquationElement {
    /**
     * Calculates the size needed to render this element.
     * @return Size object with width and height
     */
    abstract fun measure(): Size

    /**
     * Renders the element at the specified position.
     * @param drawScope The DrawScope to render into
     * @param topLeft The top-left position where the element should be drawn
     */
    abstract fun draw(drawScope: DrawScope, topLeft: Offset)

    /**
     * Overloaded plus operator to combine equation elements.
     * Optimizes consecutive text elements by merging them.
     */
    operator fun plus(other: EquationElement): EquationElement {
        return when {
            // Optimize by merging consecutive text elements
            this is Text && other is Text ->
                Text(this.text + other.text)

            // Otherwise create a horizontal group
            else ->
                HorizontalGroup(listOf(this, other))
        }
    }

    /**
     * Internal class for grouping elements horizontally.
     * Handles proper alignment and spacing of consecutive elements.
     */
    private data class HorizontalGroup(val elements: List<EquationElement>) : EquationElement() {
        override fun measure(): Size {
            var totalWidth = 0f
            var maxHeight = 0f

            // Calculate total width and maximum height of all elements
            for (element in elements) {
                val size = element.measure()
                totalWidth += size.width
                maxHeight = maxOf(maxHeight, size.height)
            }

            return Size(totalWidth, maxHeight)
        }

        override fun draw(drawScope: DrawScope, topLeft: Offset) {
            var xOffset = topLeft.x
            val sizes = elements.map { it.measure() }
            val maxHeight = sizes.maxOf { it.height }

            // Draw each element with proper vertical alignment
            for (i in elements.indices) {
                val element = elements[i]
                val size = sizes[i]

                // Calculate vertical offset to center the element
                val yOffset = topLeft.y + (maxHeight - size.height) / 2f

                element.draw(
                    drawScope,
                    Offset(xOffset, yOffset)
                )

                // Move to the next element position
                xOffset += size.width
            }
        }
    }

    /**
     * Text element for rendering basic text in equations.
     * Handles proper text positioning with baseline adjustments.
     *
     * @param text The text to display
     * @param fontSize Font size in pixels
     * @param verticalPadding Padding above and below the text
     * @param horizontalPadding Padding to the left and right of the text
     */
    data class Text(
        val text: String,
        val fontSize: Float = 80f,
        val verticalPadding: Float = 0f,
        val horizontalPadding: Float = 0f,
    ) : EquationElement() {

        // Characters that extend below the baseline
        private val descenderChars = setOf('g', 'j', 'p', 'q', 'y', ',', ';', '_')

        // Configure the paint object for text rendering
        val paint = Paint().asFrameworkPaint().apply {
            color = android.graphics.Color.BLACK
            textSize = fontSize
            isAntiAlias = true
        }

        override fun measure(): Size {
            // Get the text bounds for accurate sizing
            val bounds = Rect()
            paint.getTextBounds(text, 0, text.length, bounds)

            return Size(
                width = bounds.width().toFloat() + horizontalPadding,
                height = bounds.height().toFloat() + verticalPadding
            )
        }

        override fun draw(drawScope: DrawScope, topLeft: Offset) {
            val size = measure()

            // Check if text contains any descenders
            val hasDescenders = text.any { it.lowercaseChar() in descenderChars }

            // Get the descent (distance from baseline to bottom of text)
            val descent = paint.descent()

            // Calculate the baseline position with special handling for descenders
            val baselineY = if (hasDescenders) {
                // If there are descenders, adjust for descent
                topLeft.y + size.height - descent - verticalPadding / 2f
            } else {
                // If no descenders, position at the bottom
                topLeft.y + size.height - verticalPadding / 2f
            }

            // Draw the text using Android's native canvas
            drawScope.drawContext.canvas.nativeCanvas.drawText(
                text,
                topLeft.x + horizontalPadding / 2f,
                baselineY,
                paint
            )
        }
    }

    /**
     * Fraction element for rendering numerator/denominator with a dividing line.
     *
     * @param numerator The element to display above the line
     * @param denominator The element to display below the line
     * @param verticalPadding Space between elements and the line
     * @param lineWidth Thickness of the dividing line
     * @param lineColor Color of the dividing line
     * @param isMain Whether this is a main fraction (affects vertical positioning)
     */
    data class Fraction(
        val numerator: EquationElement,
        val denominator: EquationElement,
        val verticalPadding: Float = 20f,
        val lineWidth: Float = 3f,
        val lineColor: Color = Color.Black,
        val isMain: Boolean = true,
    ) : EquationElement() {

        override fun measure(): Size {
            val numeratorSize = numerator.measure()
            val denominatorSize = denominator.measure()

            // Width is the maximum of numerator and denominator widths
            val width = max(numeratorSize.width, denominatorSize.width)

            // Height includes both elements plus padding and line
            val height =
                numeratorSize.height + verticalPadding + lineWidth + verticalPadding + denominatorSize.height
            return Size(width, height)
        }

        override fun draw(drawScope: DrawScope, topLeft: Offset) {
            val numeratorSize = numerator.measure()
            val denominatorSize = denominator.measure()
            val fractionWidth = measure().width

            // For main fractions, center vertically in the available space
            val newTopLeft = if (isMain) {
                topLeft.copy(y = drawScope.size.height / 2f - numeratorSize.height - verticalPadding)
            } else {
                topLeft
            }

            // Draw the Numerator (centered horizontally)
            val numeratorX = newTopLeft.x + (fractionWidth - numeratorSize.width) / 2
            numerator.draw(drawScope, Offset(numeratorX, newTopLeft.y))

            // Calculate line position
            val lineTopY = newTopLeft.y + numeratorSize.height + verticalPadding
            val lineBottomY = lineTopY + lineWidth
            val lineCenterY = (lineTopY + lineBottomY) / 2

            // Draw the dividing line
            drawScope.drawLine(
                color = lineColor,
                start = Offset(newTopLeft.x, lineCenterY),
                end = Offset(newTopLeft.x + fractionWidth, lineCenterY),
                strokeWidth = lineWidth
            )

            // Draw the Denominator (centered horizontally)
            val denominatorY = lineBottomY + verticalPadding
            val denominatorX = newTopLeft.x + (fractionWidth - denominatorSize.width) / 2
            denominator.draw(drawScope, Offset(denominatorX, denominatorY))
        }
    }

    /**
     * Superscript element for rendering exponents and powers.
     *
     * @param base The base element
     * @param power The superscript element
     * @param powerScaleFactor Scale factor for the superscript (0.5 = half size)
     * @param verticalPadding Space between base and power
     * @param isMain Whether this is a main element (affects vertical positioning)
     */
    data class Superscript(
        val base: EquationElement,
        val power: EquationElement,
        val powerScaleFactor: Float = 0.5f,
        val verticalPadding: Float = 15f,
        val isMain: Boolean = true
    ) : EquationElement() {

        override fun measure(): Size {
            val baseSize = base.measure()
            val powerSize = power.measure()
            val scaledPowerSize = powerSize * powerScaleFactor

            // Total width includes base width plus scaled power width
            val width = baseSize.width + scaledPowerSize.width

            // Total height accounts for vertical positioning of power
            val height = baseSize.height + scaledPowerSize.height + verticalPadding
            return Size(width, height)
        }

        override fun draw(drawScope: DrawScope, topLeft: Offset) {
            val baseSize = base.measure()
            val powerSize = power.measure()
            val scaledPowerHeight = powerSize.height * powerScaleFactor

            // For main elements, center vertically
            val newTopLef = if (isMain) {
                topLeft.copy(y = topLeft.y - baseSize.height / 2f)
            } else {
                topLeft
            }

            // Position the base element
            val topLeftForBase = Offset(
                x = newTopLef.x,
                y = newTopLef.y + scaledPowerHeight + verticalPadding
            )
            base.draw(drawScope, topLeftForBase)

            // Position the power element (scaled)
            val topLeftForPowerY = topLeftForBase.y - scaledPowerHeight - verticalPadding
            val topLeftForPower = Offset(
                x = topLeftForBase.x + baseSize.width,
                y = topLeftForPowerY
            )

            // Draw the power with scaling
            drawScope.scale(powerScaleFactor, pivot = topLeftForPower) {
                power.draw(this, topLeftForPower)
            }
        }
    }

    /**
     * Subscript element for rendering indices and other subscripts.
     *
     * @param base The base element
     * @param sub The subscript element
     * @param subScaleFactor Scale factor for the subscript (0.5 = half size)
     * @param topPadding Space above the subscript
     * @param startPadding Space between base and subscript
     */
    data class Subscript(
        val base: EquationElement,
        val sub: EquationElement,
        val subScaleFactor: Float = 0.5f,
        val topPadding: Float = 20f,
        val startPadding: Float = 20f
    ) : EquationElement() {
        override fun measure(): Size {
            val baseSize = base.measure()
            val subSize = sub.measure()
            // Scale down the subscript
            val scaledSubSize = subSize * subScaleFactor

            return baseSize + scaledSubSize
        }

        override fun draw(drawScope: DrawScope, topLeft: Offset) {
            val baseSize = base.measure()

            // Draw the base
            base.draw(drawScope, topLeft)

            // Position the subscript
            val topLeftForSub = Offset(
                x = topLeft.x + baseSize.width + startPadding,
                y = topLeft.y + baseSize.height + topPadding
            )

            // Draw the subscript with scaling
            drawScope.scale(subScaleFactor) {
                sub.draw(
                    this,
                    topLeftForSub
                )
            }
        }
    }

    /**
     * Radical element for rendering square roots and nth roots.
     *
     * @param radicand The element under the radical
     * @param index Optional index element for nth roots
     * @param indexScaleFactor Scale factor for the index (0.5 = half size)
     * @param lineColor Color of the radical symbol
     * @param lineWidth Thickness of the radical symbol
     */
    data class Radical(
        val radicand: EquationElement,
        val index: EquationElement? = null,
        val indexScaleFactor: Float = 0.5f,
        val lineColor: Color = Color.Black,
        val lineWidth: Float = 2.5f
    ) : EquationElement() {
        // Constants for layout measurements
        companion object {
            private const val RADICAND_HORIZONTAL_PADDING = 10f
            private const val RADICAND_VERTICAL_PADDING = 8f
            private const val TOP_PADDING = 5f
            private const val TOP_LINE_EXTENSION = 5f
            private const val HOOK_DEPTH_RATIO = 0.15f
            private const val VINCULUM_VERTICAL_OFFSET = 4f
            private const val INDEX_HORIZONTAL_PADDING = 5f
            private const val INDEX_VERTICAL_PADDING = 6f
        }

        override fun measure(): Size {
            val radicandSize = radicand.measure()
            val indexSize = index?.measure() ?: Size.Zero
            val scaledIndexWidth = indexSize.width * indexScaleFactor
            val scaledIndexHeight = indexSize.height * indexScaleFactor

            // Calculate symbol width based on radicand height
            val symbolWidth = radicandSize.height * 0.7f

            // Account for index in total width if present
            val indexWidthContribution = if (index != null) {
                max(0f, scaledIndexWidth - symbolWidth * 0.3f) + INDEX_HORIZONTAL_PADDING
            } else {
                0f
            }

            // Calculate total width with increased padding
            val totalWidth = indexWidthContribution +
                    symbolWidth +
                    RADICAND_HORIZONTAL_PADDING * 2 +
                    radicandSize.width +
                    TOP_LINE_EXTENSION

            // Account for index in total height if present
            val indexHeightContribution = if (index != null) {
                scaledIndexHeight + INDEX_VERTICAL_PADDING
            } else {
                0f
            }

            // Calculate total height with increased padding
            val totalHeight = max(
                radicandSize.height + RADICAND_VERTICAL_PADDING * 2 + TOP_PADDING,
                indexHeightContribution + radicandSize.height * 0.85f + RADICAND_VERTICAL_PADDING
            )

            return Size(
                width = totalWidth,
                height = totalHeight
            )
        }

        override fun draw(drawScope: DrawScope, topLeft: Offset) {
            val radicandSize = radicand.measure()
            val indexSize = index?.measure() ?: Size.Zero
            val scaledIndexSize = Size(
                indexSize.width * indexScaleFactor,
                indexSize.height * indexScaleFactor
            )

            // Calculate dimensions with increased padding
            val totalHeight = radicandSize.height + RADICAND_VERTICAL_PADDING * 2 + TOP_PADDING
            val symbolWidth = radicandSize.height * 0.7f

            // Adjust starting position if index extends to the left
            // Adjust starting position if index extends to the left
            var adjustedTopLeft = topLeft
            if (index != null) {
                val indexWidth = scaledIndexSize.width + INDEX_HORIZONTAL_PADDING
                if (indexWidth > symbolWidth * 0.3f) {
                    adjustedTopLeft = Offset(
                        topLeft.x + (indexWidth - symbolWidth * 0.3f),
                        topLeft.y
                    )
                }
            }

            // Radicand position with increased padding
            val radicandX = adjustedTopLeft.x + symbolWidth + RADICAND_HORIZONTAL_PADDING
            val radicandY = adjustedTopLeft.y + RADICAND_VERTICAL_PADDING + TOP_PADDING

            // Hook calculations
            val hookStartX = adjustedTopLeft.x
            val hookBottomY = adjustedTopLeft.y + totalHeight
            val hookDepth = totalHeight * HOOK_DEPTH_RATIO

            // Vinculum (top horizontal line)
            val vinculumY = adjustedTopLeft.y + VINCULUM_VERTICAL_OFFSET + TOP_PADDING
            val vinculumEndX = radicandX + radicandSize.width + RADICAND_HORIZONTAL_PADDING

            // Draw radical symbol using a path
            val path = Path().apply {
                // Start at left of hook
                moveTo(hookStartX, hookBottomY - hookDepth)

                // Draw hook bottom
                lineTo(hookStartX + symbolWidth * 0.3f, hookBottomY)

                // Draw diagonal line up
                lineTo(adjustedTopLeft.x + symbolWidth, vinculumY)

                // Draw horizontal line (vinculum) with extra padding
                lineTo(vinculumEndX, vinculumY)
            }

            drawScope.drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = lineWidth)
            )

            // Draw index if present
            index?.let {
                // Position index to align with the hook with proper padding
                val indexX = hookStartX - INDEX_HORIZONTAL_PADDING
                val indexY =
                    hookBottomY - hookDepth - scaledIndexSize.height - INDEX_VERTICAL_PADDING

                // Draw the index with scaling
                drawScope.scale(indexScaleFactor, pivot = Offset(indexX, indexY)) {
                    it.draw(this, Offset(indexX, indexY))
                }
            }

            // Draw the radicand with increased padding
            radicand.draw(
                drawScope = drawScope,
                topLeft = Offset(radicandX, radicandY)
            )
        }
    }

    /**
     * Matrix element for rendering matrices with brackets.
     *
     * @param rows Number of rows in the matrix
     * @param columns Number of columns in the matrix
     * @param elements 2D list of elements to display in the matrix
     */
    data class Matrix(
        val rows: Int,
        val columns: Int,
        val elements: List<List<EquationElement>>
    ) : EquationElement() {
        // Constants for layout measurements
        companion object {
            private const val BRACKET_WIDTH = 10f
            private const val BRACKET_THICKNESS = 2f
            private const val HORIZONTAL_CELL_PADDING = 15f
            private const val VERTICAL_CELL_PADDING = 10f
            private const val BRACKET_PADDING = 8f
        }

        override fun measure(): Size {
            // Calculate cell sizes and row heights
            val cellSizes = Array(rows) { Array(columns) { Size.Zero } }
            val rowHeights = FloatArray(rows)
            val columnWidths = FloatArray(columns)

            // Measure each cell and determine maximum dimensions
            for (i in 0 until rows) {
                for (j in 0 until columns) {
                    val element = elements[i][j]
                    val size = element.measure()
                    cellSizes[i][j] = size
                    rowHeights[i] = maxOf(rowHeights[i], size.height)
                    columnWidths[j] = maxOf(columnWidths[j], size.width)
                }
            }

            // Calculate total width and height
            val totalWidth = columnWidths.sum() +
                    (columns - 1) * HORIZONTAL_CELL_PADDING +
                    2 * BRACKET_WIDTH +
                    2 * BRACKET_PADDING

            val totalHeight = rowHeights.sum() +
                    (rows - 1) * VERTICAL_CELL_PADDING +
                    2 * BRACKET_PADDING

            return Size(totalWidth, totalHeight)
        }

        override fun draw(drawScope: DrawScope, topLeft: Offset) {
            // Calculate cell sizes and row heights
            val cellSizes = Array(rows) { Array(columns) { Size.Zero } }
            val rowHeights = FloatArray(rows)
            val columnWidths = FloatArray(columns)

            // Measure each cell and determine maximum dimensions
            for (i in 0 until rows) {
                for (j in 0 until columns) {
                    val element = elements[i][j]
                    val size = element.measure()
                    cellSizes[i][j] = size
                    rowHeights[i] = maxOf(rowHeights[i], size.height)
                    columnWidths[j] = maxOf(columnWidths[j], size.width)
                }
            }

            // Calculate total matrix height and width
            val totalHeight = rowHeights.sum() + (rows - 1) * VERTICAL_CELL_PADDING
            val totalWidth = columnWidths.sum() + (columns - 1) * HORIZONTAL_CELL_PADDING

            // Draw left bracket
            val leftBracketPath = Path().apply {
                // Top horizontal line
                moveTo(topLeft.x, topLeft.y)
                lineTo(topLeft.x + BRACKET_WIDTH, topLeft.y)

                // Vertical line
                moveTo(topLeft.x, topLeft.y)
                lineTo(topLeft.x, topLeft.y + totalHeight + 2 * BRACKET_PADDING)

                // Bottom horizontal line
                moveTo(topLeft.x, topLeft.y + totalHeight + 2 * BRACKET_PADDING)
                lineTo(topLeft.x + BRACKET_WIDTH, topLeft.y + totalHeight + 2 * BRACKET_PADDING)
            }

            drawScope.drawPath(
                path = leftBracketPath,
                color = Color.Black,
                style = Stroke(width = BRACKET_THICKNESS)
            )

            // Draw right bracket
            val rightBracketX = topLeft.x + totalWidth + 2 * BRACKET_WIDTH + HORIZONTAL_CELL_PADDING
            val rightBracketPath = Path().apply {
                // Top horizontal line
                moveTo(rightBracketX, topLeft.y)
                lineTo(rightBracketX - BRACKET_WIDTH, topLeft.y)

                // Vertical line
                moveTo(rightBracketX, topLeft.y)
                lineTo(rightBracketX, topLeft.y + totalHeight + 2 * BRACKET_PADDING)

                // Bottom horizontal line
                moveTo(rightBracketX, topLeft.y + totalHeight + 2 * BRACKET_PADDING)
                lineTo(rightBracketX - BRACKET_WIDTH, topLeft.y + totalHeight + 2 * BRACKET_PADDING)
            }

            drawScope.drawPath(
                path = rightBracketPath,
                color = Color.Black,
                style = Stroke(width = BRACKET_THICKNESS)
            )

            // Draw matrix elements
            var yOffset = topLeft.y + BRACKET_PADDING

            for (i in 0 until rows) {
                var xOffset = topLeft.x + BRACKET_WIDTH + BRACKET_PADDING

                for (j in 0 until columns) {
                    val element = elements[i][j]
                    val elementSize = cellSizes[i][j]

                    // Center element within its cell
                    val elementX = xOffset + (columnWidths[j] - elementSize.width) / 2
                    val elementY = yOffset + (rowHeights[i] - elementSize.height) / 2

                    element.draw(
                        drawScope,
                        Offset(elementX, elementY)
                    )

                    xOffset += columnWidths[j] + HORIZONTAL_CELL_PADDING
                }

                yOffset += rowHeights[i] + VERTICAL_CELL_PADDING
            }
        }
    }
}

/**
 * Extension function to add two Size objects together.
 * Used for combining dimensions in layout calculations.
 */
operator fun Size.plus(other: Size): Size {
    return Size(width + other.width, height + other.height)
}