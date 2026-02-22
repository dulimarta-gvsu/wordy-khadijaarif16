package edu.gvsu.cis.worder

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.gvsu.cis.worder.ui.theme.WorderTheme

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun GameScreen(modifier: Modifier = Modifier, viewModel: AppViewModel) {
    val stockLetters by viewModel.sourceLetters.collectAsState()
    val arrangedLetters by viewModel.targetLetters.collectAsState()
    val tScore by viewModel.totalScore.collectAsState()
    val wScore by viewModel.wordScore.collectAsState()
    val words by viewModel.wordsFound.collectAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(top = 24.dp)
    ) {
        //need a row of buttons
        Row(
            modifier = Modifier.align(Alignment.TopCenter)

        ){
            Button(
                onClick = {
                    viewModel.selectRandomLetters()
                },
            ) {
                Text("New Game")
            }
            Button(
                onClick = {
                    viewModel.ReshuffleRemaining()
                },
            ) {
                Text("Reshuffle")
            }
            Button(
                onClick = {
                    viewModel.submitWord()
                },
                enabled = wScore>0
            ) {
                Text("Record Word")
            }
        }


        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LetterGroup(letters = arrangedLetters, groupId = "Top") {
                viewModel.rearrangeLetters(Origin.CenterBox, it.filterNotNull() )
            }
            LetterGroup(letters = stockLetters, groupId = "Bottom") {
                println("Bottom box rearrange $it")
                viewModel.rearrangeLetters(Origin.Stock, it.filterNotNull())
            }
            //Display scores
            Text("Words Recorded: $words")
            Text("Word Score: $wScore")
            Text("Total Score: $tScore")
//            Button(onClick = { val wordFound = viewModel.submitWord()}){
//                Text("Submit")
//            }
        }
    }
}

@Composable
fun BigLetter(modifier: Modifier = Modifier, letter: Letter?, cellSize: Dp = 48.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(cellSize)
            .border(width = 1.dp, color = Color.Black, shape = RoundedCornerShape(8.dp))
            .background(
                if (letter == null) Color.Transparent else Color.Green,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(3.dp)
    ) {
        Text(
            letter?.text?.toString() ?: "",
            fontSize = (cellSize * 0.7f).value.sp,
            textAlign = TextAlign.Center
        )

        //have to display if mre than 1
        if (letter != null && letter.letterMultiplier > 1) {
            Text(
                text = "L${letter.letterMultiplier}",
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
        if(letter!=null && letter.wordMultiplier>1)
        {
            Text(
                text = "W${letter.wordMultiplier}",
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
        //just the point value
        if(letter!= null){
            Text(
                text = letter.point.toString(),
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }

}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun LetterGroup(
    modifier: Modifier = Modifier, groupId: String,
    letters: List<Letter?>,
    onRearranged: (List<Letter?>) -> Unit
) {
    val configuration = LocalConfiguration.current
    val letterSize = (configuration.screenWidthDp.dp - 24.dp) /
            letters.size.coerceAtLeast(1)
    var borderColor by remember { mutableStateOf(Color.LightGray) }
    var boxBound by remember { mutableStateOf(Rect.Zero) }
    var emptyCellIndex by remember { mutableStateOf<Int?>(null) }
    var startDragIndex by remember { mutableStateOf<Int?>(null) }
    var draggedLetter by remember { mutableStateOf<Letter?>(null) }
    val mutLetters = remember { mutableStateListOf<Letter?>() }
    LaunchedEffect(letters) {
        // Recreate the mutable list when the letter list changed
        mutLetters.clear()
        mutLetters.addAll(letters)
    }

    // Convert pointer offset to letter cell index
    fun offsetToIndex(xOffset: Float): Int {
        val N = mutLetters.size
        if (N > 0) {
            val cellWidth = boxBound.width / N
            val offsetFromLeft = xOffset - boxBound.left
            val idx = (offsetFromLeft / cellWidth).toInt()
            return idx.coerceAtMost(N - 1)
        }
        return 0
    }

    val ddTarget = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val ev = event.toAndroidDragEvent()
                val dropData = ev.clipData.getItemAt(0).text
                // Decode the string payload (text and point separated by '/')
                val (text,point, lMult, wMult) = dropData.split("/")
                val letterObject = Letter(text.first(),point.toInt(), lMult.toInt(), wMult.toInt())
                // Drop the letter to the empty cell
                if (emptyCellIndex != null) {
                    mutLetters[emptyCellIndex!!] = letterObject
                }
                emptyCellIndex = null // no more empty cell now
                return true
            }

            override fun onEntered(event: DragAndDropEvent) {
                super.onEntered(event)
                // use darker border
                borderColor = Color.DarkGray
            }

            override fun onExited(event: DragAndDropEvent) {
                super.onExited(event)
                if (emptyCellIndex != null && emptyCellIndex!! < mutLetters.size) {
                    mutLetters.removeAt(emptyCellIndex!!)
                }
                emptyCellIndex = null
                borderColor = Color.LightGray
            }

            override fun onMoved(event: DragAndDropEvent) {
                super.onMoved(event)
                val ev = event.toAndroidDragEvent()
                val pointerIndex = offsetToIndex(ev.x)

                // After pointer exit, emptyCellIndex was set to null
                if (emptyCellIndex == null) {
                    // No empty cell yet, we need to insert one
                    if (mutLetters.isEmpty())
                        mutLetters.add(null)
                    else
                        mutLetters.add(pointerIndex, null)
                } else if (pointerIndex != emptyCellIndex!!) {
                    mutLetters.removeAt(emptyCellIndex!!)
                    mutLetters.add(pointerIndex, null)
                }
                emptyCellIndex = pointerIndex
            }

            override fun onEnded(event: DragAndDropEvent) {
                super.onEnded(event)
                val ev = event.toAndroidDragEvent()
                if (ev.result) {
                    // The letter was dropped
                    onRearranged(mutLetters.toList())
                } else if (startDragIndex != null) {
                    // Dragging gesture did not drop the letter, put the letter back
                    mutLetters.add(startDragIndex!!, draggedLetter)
                }
                emptyCellIndex = null
                startDragIndex = null
                draggedLetter = null
                borderColor = Color.LightGray
            }
        }
    }
    Column {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .defaultMinSize(72.dp, minHeight = 72.dp)
//                .defaultMinSize(96.dp)
                .border(width = 3.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
                .padding(8.dp)
                .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = ddTarget)) {
            LazyRow(modifier = Modifier.onGloballyPositioned{
                boxBound = it.boundsInRoot()

            }) {
                // Can't use only position as key: reordering won't work correctly
                // Can't use only character as key: the list may contain duplicate letters
                itemsIndexed(mutLetters, key = { pos, item -> "$pos-" + (item?.text ?: "#") }) { pos, lx ->
                    BigLetter(letter = lx, cellSize = letterSize.coerceAtMost(80.dp),
                        modifier = Modifier.dragAndDropSource {
                        detectTapGestures(onLongPress = {
                            startDragIndex = pos
                            draggedLetter = lx
                            mutLetters[pos] = null
                            emptyCellIndex = pos
                            this.startTransfer(
                                transferData = DragAndDropTransferData(
                                    clipData = ClipData.newPlainText(
                                        "",
                                        // Some hack here: unpack the object details as a string
                                        "${lx?.text ?: "$"}/${lx?.point}/${lx?.letterMultiplier}/${lx?.wordMultiplier}"
                                    )
                                )
                            )
                        })
                    })
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GameScreenPreview() {
    WorderTheme {
        val appVM = AppViewModel(android.app.Application())
        GameScreen(viewModel = appVM)
    }
}