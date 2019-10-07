abstract class SolverStrategy {
    protected abstract fun invokeUncached(board: Board): Boolean
    operator fun invoke(board: Board): Boolean {
        val currentList = board.saveList()
        val cached = cache.getOrDefault(currentList, null)
        if (cached != null) {
            cacheHitRate++
            if (cached == currentList)
                return false
            board.reloadList(cached)
            return true
        }
        cacheMissRate++
        val result = invokeUncached(board)
        val newList = board.saveList()
        cache[currentList] = newList
        assert ((currentList == newList) == !result)
        return result
    }
    private val cache: MutableMap<List<Short>, List<Short>> = mutableMapOf()
    var cacheHitRate: Int = 0
        private set
    var cacheMissRate: Int = 0
        private set
}

object NakedSingle: SolverStrategy() {
    override fun invokeUncached(board: Board): Boolean {
        var result = false
        for (line in 0..8)
            for (col in 0..8)
                result = invokeUncached(board[line, col]) || result
        return result
    }
    private fun invokeUncached(cell: Cell): Boolean {
        if (cell.solved)
            return false
        val visibleMask: Int = cell.parentBoard.getSolvedVisible(cell)
        if (visibleMask and cell.bitmask == 0)
            return false
        cell.bitmask = cell.bitmask and visibleMask.inv()
        return true
    }
}

object HiddenSingles: SolverStrategy() {
    override fun invokeUncached(board: Board): Boolean {
        var result = false
        for (clue in 0..8)
            for (line in 0..8)
                for (col in 0..8)
            result = invokeUncached(board[line, col]) || result
        return result
    }
    private fun invokeUncached(cell: Cell): Boolean {
        if (cell.solved)
            return false // Nothing to remove from active cell
        // Check if any of the possibilities is alone in a primitive
        for (clue in cell.possibilities)
            if (invokeUncached(cell, clue))
                return true
        return false
    }
    private fun invokeUncached(cell: Cell, clue: Int): Boolean {
        // Check if this clue is alone in either row, column or primitive
        if (cell.parentBoard.getLineCount(cell.line, clue) == 1)
            return cell.setClue(clue)
        if (cell.parentBoard.getColCount(cell.col, clue) == 1)
            return cell.setClue(clue)
        if (cell.parentBoard.getBoxCount(cell.box, clue) == 1)
            return cell.setClue(clue)
        return false
    }
}

object SimpleStrategies: SolverStrategy() {
    private val strats: Set<SolverStrategy> = setOf(NakedSingle, HiddenSingles)
    override fun invokeUncached(board: Board): Boolean {
        var result = false
        for (strat in strats)
            result = result or strat(board)
        return result
    }
}

object LoopedSimpleStrategies: SolverStrategy() {
    override fun invokeUncached(board: Board): Boolean {
        var result = false
        while (SimpleStrategies(board))
            result = true
        return result
    }
}

object SplitStrategy: SolverStrategy() {
    override fun invokeUncached(board: Board): Boolean {
        return withStrat(board, LoopedSimpleStrategies)
    }
    // Put a cache around this one too
    fun withStrat(board: Board, solverStrategy: SolverStrategy): Boolean {
        var result = false
        for (line in 0..8)
            for (col in 0..8) {
                result = this.withStrat(board[line, col], solverStrategy) || result
                //if (result) return true
            }
        return result
    }
    private fun withStrat(cell: Cell, solverStrategy: SolverStrategy): Boolean {
        // We will create boards where each possibility is the same
        if (cell.possibilities.size < 2)
            return false // Nothing to do
        val boards = mutableListOf<Board>()
        for (pos in cell.possibilities) {
            val b = cell.parentBoard.clone()
            b[cell.line, cell.col].setClue(pos)
            solverStrategy(b)
            if (b.valid)
                boards.add(b)
        }
        assert(boards.size > 0)
        if (boards.size == 1) {
            return cell.setClue(boards[0][cell.line, cell.col].solvedValue!!)
        }
        var result = false
        // We shall iterate through all cells; if a clue is missing from any other cell we can remove it
        for (line in 0..8)
            for (col in 0..8) {
                val localCell = cell.parentBoard[line, col]
                val localbitmask = localCell.bitmask
                var targetbitmask = 0
                for (b in boards)
                    targetbitmask = targetbitmask or b[line, col].bitmask
                if (targetbitmask != localbitmask) {
                    result = true
                    // Really? This guy should be optimizable to hell
                    for (shift in 0..8) {
                        val isLocal = (1 shl shift) and localbitmask != 0
                        val isTarget = (1 shl shift) and targetbitmask != 0
                        if (isLocal && !isTarget) {
                            localCell.removeClue(shift)
                        }
                    }
                }
            }
        // We are done
        return result
    }
}

object LoopedStrategies: SolverStrategy() {
    override fun invokeUncached(board: Board): Boolean {
        var result = false
        while (SimpleStrategies(board) || SplitStrategy(board))
            result = true
        return result
    }
}

object SplitStrategy2: SolverStrategy() {
    override fun invokeUncached(board: Board): Boolean = SplitStrategy.withStrat(board, LoopedStrategies)
}

object LoopedStrategies2: SolverStrategy() {
    override fun invokeUncached(board: Board): Boolean {
        var result = false
        while (LoopedStrategies(board) || SplitStrategy2(board))
            result = true
        return result
    }
}