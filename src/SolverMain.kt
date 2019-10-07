import kotlin.system.measureTimeMillis

fun atMostOneBit(i: Int) = i and (i-1) == 0
fun exactlyOneBit(i: Int) = i != 0 && atMostOneBit(i)
@UseExperimental(ExperimentalStdlibApi::class)
fun maskToClue(i: Int) = i.countTrailingZeroBits()

class Board {
    private val board : Array<Short> = Array(81) { 511.toShort() }

    // These hold the counts of notes for each clue on every one of the primitives
    private val lines = Array(9) { Array(9) { 9 } }
    private val columns = Array(9) { Array(9) { 9 } }
    private val boxes = Array(9) { Array(9) { 9 } }
    // I may want to hold a bitmask instead for each clue and each of the squares inside the primitives?
    // Could it be helpful?

    // For each line keep a bitmask of cells that are solved
    private val solvedLines = Array(9) {0}
    private val solvedColumns = Array(9) {0}
    private val solvedBoxes = Array(9) {0}

    operator fun get(line: Int, col: Int): Cell {
        require(line in 0..8)
        require(col in 0..8)
        val off = (line) * 9 + (col)
        return object : Cell {
            override var bitmask: Int
                get() = board[off].toInt()
                set(value) {
                    val added = value and bitmask.inv()
                    val removed = bitmask and value.inv()
                    if (solved) {
                        solvedLines[line] = solvedLines[line] and (1 shl solvedValue!!).inv()
                        solvedColumns[col] = solvedColumns[col] and (1 shl solvedValue!!).inv()
                        solvedBoxes[box] = solvedBoxes[box] and (1 shl solvedValue!!).inv()
                    }
                    board[off] = value.toShort()
                    if (solved) {
                        solvedLines[line] = solvedLines[line] or (1 shl solvedValue!!)
                        solvedColumns[col] = solvedColumns[col] or (1 shl solvedValue!!)
                        solvedBoxes[box] = solvedBoxes[box] or (1 shl solvedValue!!)
                    }
                    if (value == 0)
                        valid = false
                    for (clue in 0..8) {
                        if (added and (1 shl clue) != 0) {
                            lines[clue][line]++
                            columns[clue][col]++
                            boxes[clue][box]++
                        }
                        if (removed and (1 shl clue) != 0) {
                            if (lines[clue][line]-- == 1)
                                valid = false
                            if (columns[clue][col]-- == 1)
                                valid = false
                            if (boxes[clue][box]-- == 1)
                                valid = false
                        }
                    }
                }
            override val col = col
            override val line = line
            override val parentBoard = this@Board
            override val box = super.box // Optimize, convert getter to initializer
        }
    }

    override fun toString(): String {
        var result = ""
        for (lineset in 0 until 3) {
            for (line in lineset * 3 until (lineset+1) * 3) {
                for (colset in 0 until 3) {
                    for (col in colset * 3 until (colset+1) * 3) {
                        result += this[line, col].str
                        result += ' '
                    }
                    result += ' '
                }
                result += '\n'
            }
            result += '\n'
        }
        return result
    }

    fun getLineCount(line: Int, clue: Int) = lines[clue][line]
    fun getColCount(col: Int, clue: Int) = columns[clue][col]
    fun getBoxCount(box: Int, clue: Int) = boxes[clue][box]
    private fun getSolvedInLine(line: Int) = solvedLines[line]
    private fun getSolvedInColumn(col: Int) = solvedColumns[col]
    private fun getSolvedInBox(box: Int) = solvedBoxes[box]
    fun getSolvedVisible(cell: Cell) =
        getSolvedInLine(cell.line) or getSolvedInColumn(cell.col) or getSolvedInBox(cell.box)

    var valid: Boolean = true
        private set

    fun clone(): Board {
        val result = Board()
        for (off in 0 until 81)
            result.board[off] = board[off]
        for (x in 0 until 9) {
            for (y in 0 until 9) {
                result.lines[x][y] = lines[x][y]
                result.columns[x][y] = columns[x][y]
                result.boxes[x][y] = boxes[x][y]
            }
            result.solvedLines[x] = solvedLines[x]
            result.solvedColumns[x] = solvedColumns[x]
            result.solvedBoxes[x] = solvedBoxes[x]
        }
        result.valid = valid
        return result
    }

    fun saveList(): List<Short> = board.clone().asList()

    fun reloadList(list: List<Short>) {
        require(list.size >= 81)
        for (elem in list.take(81))
            require(elem in 0 until 512)
        for (i in 0 until 81) {
            board[i] = 511
        }
        for (x in 0 until 9) {
            for (y in 0 until 9) {
                lines[x][y] = 9
                columns[x][y] = 9
                boxes[x][y] = 9
            }
            solvedLines[x] = 0
            solvedColumns[x] = 0
            solvedBoxes[x] = 0
        }
        valid = true
        for (i in 0 until 81) {
            val line = i / 9
            val col = i % 9
            this[line, col].bitmask = list[i].toInt()
        }
    }
}

interface Cell {
    val possibilities: Set<Int> get() {
        val result = mutableSetOf<Int>()
        for (shift in 0..8)
            if (bitmask and (1 shl shift) != 0)
                result.add(shift)
        return result
    }
    val solved: Boolean get() = exactlyOneBit(bitmask)
    val solvedValue: Int? get() = if (solved) possibilities.elementAt(0) else null
    var bitmask: Int
    val str: String get() {
        if (bitmask == 511)
            return "*"
        if (bitmask == 0)
            return "[NONE]"
        if (solved)
            return "${solvedValue!!+1}"
        var result = "["
        for (it in possibilities)
            result += (it+1).toString()
        result += "]"
        return result
    }
    fun removeClue(clue: Int): Boolean {
        require(clue in 0..8)
        val newmask = bitmask and (1 shl clue).inv()
        if (bitmask == newmask)
            return false
        bitmask = newmask
        return true
    }
    val parentBoard: Board
    val line: Int
    val col: Int
    fun setClue(clue: Int): Boolean {
        require(clue in 0..8)
        val newMask = 1 shl clue
        if (bitmask == newMask)
            return false
        bitmask = newMask
        return true
    }
    val box: Int get() = (line) / 3 * 3 + (col) / 3
}

fun main() {
    val board = Board()
    val game = "8**********36******7**9*2***5***7*******457*****1***3***1****68**85***1**9****4**"
    for (line in 0..8)
        for (col in 0..8) {
            val off = line * 9 + col
            val chr = game[off]
            if (chr !in '1'..'9')
                continue
            val item = chr - '1'
            board[line, col].setClue(item)
        }
    println(board)
    val time = measureTimeMillis { LoopedStrategies2(board) }
    println(board)
    println("Naked single ran: ${NakedSingle.cacheMissRate} (plus ${NakedSingle.cacheHitRate} which hit the cache)")
    println("Hidden single ran: ${HiddenSingles.cacheMissRate} (plus ${HiddenSingles.cacheHitRate} which hit the cache)")
    println("Split strategy ran: ${SplitStrategy.cacheMissRate} (plus ${SplitStrategy.cacheHitRate} which hit the cache)")
    println("Second level split strategy ran: ${SplitStrategy2.cacheMissRate} (plus ${SplitStrategy2.cacheHitRate} which hit the cache)")
    println("Time (in milliseconds): $time")
}

// These are games that I did manage to solve, faster or slower
val solvedGames = listOf(
    "5***1***4274***6***8*9*4***81*46*3*2**2*3*1**7*6*91*58***5*3*1***5***9271***2***3",
    "**************3*85**1*2*******5*7*****4***1***9*******5******73**2*1********4***9",
    "**7**1*8**********59**2*6131*54*6*3*36**1***************6**54***5***237****374**1",
    "4.....8.5.3..........7......2.....6.....8.4......1.......6.3.7.5..2.....1.4......",
    "52...6.........7.13...........4..8..6......5...........418.........3..2...87.....",
    "6.....8.3.4.7.................5.4.7.3..2.....1.6.......2.....5.....8.6......1....",
    "48.3............71.2.......7.5....6....2..8.............1.76...3.....4......5....",
    "....14....3....2...7..........9...3.6.1.............8.2.....1.4....5.6.....7.8...",
    "......52..8.4......3...9...5.1...6..2..7........3.....6...1..........7.4.......3.",
    "6.2.5.........3.4..........43...8....1....2........7..5..27...........81...6.....",
    ".524.........7.1..............8.2...3.....6...9.5.....1.6.3...........897........",
    "6.2.5.........4.3..........43...8....1....2........7..5..27...........81...6.....",
    ".923.........8.1...........1.7.4...........658.........6.5.2...4.....7.....9.....",
    "5=1====4======1=====4=95==22---3-----6-----8----8--7-9========6=9=4===3=63===2===",
    "???4???8??13????7?5??7?63???8?????????21?4????????519?????5?6????13????2?37?????8",
    "6..1..........2..3..7.9...2....5...4.2..3..1....7...8.5..8...7....6..4....4.....8",
    "..2..1.4..51.94....4.26....,1,,45,,,,36,,,51,,,,31,,2,....26.5....48.36..2.1..4..",
    "....5..6.6..9....3.5....2....8....5.9....3..4.1..2....4..6...7...1...8...2...8..1",
    "97....6...1..269.5...3...1.1..9.45...4..5..91..9..8..4....4...3...5..12..61..7...",
    "8....63.5.4.....7...........1..387.4...1.4...3...7.29......3....2.....4.5.68....2", // Fast
    ".23..59...84....6....7..........8....6....24...29..13...94....3....2....1....65..",
    "4..3.6..9..1..4.7..5.8..2.....6..4.......8....8..4..1...8..3....4..5..9....1.....",
    "17.4.....3.8...1.........53...8.1..4..7...2......26.7....1.....8.4...5..6.9..3.2.",
    "..12....8..34....25....31..6....18.............93....5..48....31....67..8....45..",
    "...1..5....7.....6..6..2.4...8....9..5..6..7..3....2...2.9..3..8.....1....3..8...",
    "..56.......87.....2....91..9....82.............72....4..65....7.....14.......23..",
    "12.3.4.783..8.7..9.........29.....84....8....78.....13.........4..9.2..693.1.8.47",
    "63.....81.2...3.......1743..964..57....762....8....6...6..2....3.9....6.........9",
    "..5..4....6..1..3..7.8..9.2.4......8..3...2..2......1.8.9..3.4..1..5..9....7..5..", // instant
    ".7..6...96..5...8...4...7...3...6...2...5...8...4...1...3...4...2...7..31...8..2.",
    "...96..2....12.7...7..5....1....59.4.2......5..94...6...7.......95.4......36...1.",
    "......3.4.....2......6.......1.4.......5...2.......68....13.5..26......78........",
    "....4.3...4.9758..8.......4.7..2.4.....6.1.....5.9..2.6.......9..9362.4...7.8....",
    "1....86.2...1...9....6..47.2.3....69....4....97....2.4.69..5....5...2...7.29....8",
    "..8.4..626.9.......3.6..9...4.96....2..1.4..6....82.1...5..8.3.......2.536..1.7..",
    ",,,7,,,,,1,,,,,,,,,,,43,2,,,,,,,,,,6,,,5,9,,,,,,,,,418,,,,81,,,,,2,,,,5,,4,,,,3,,", // Reasonable
    "380000000000400785009020300060090000800302009000040070001070500495006000000000092" // They claimed this was hard
)
// These are games that I could solve but took some extra work
val hardGames = listOf(
    "8**********36******7**9*2***5***7*******457*****1***3***1****68**85***1**9****4**", // Hardest
    "100007090030020008009600500005300900010080002600004000300000010040000007007000300", // "AI Escargot"
    "000003017015009008060000000100007000009000200000500004000000020500600340340200000", // Mildly hard
    "000700800006000031040002000024070000010030080000060290000800070860000500002006000" // Mildly hard as well
)