package io.github.memo33.fshgen

import scala.concurrent.{ ExecutionContext, Future, Await }
import scala.concurrent.duration.Duration.Inf
import java.util.concurrent.{ BlockingQueue, Executors, ArrayBlockingQueue }

object ParItr {

  def map[A, B](i: Iterator[A])(f: A => B)(implicit execctx: ExecutionContext): Iterator[B] = {
    val cpus = Runtime.getRuntime().availableProcessors() + 1
//    val queue: BlockingQueue[Option[Future[B]]] = new ArrayBlockingQueue(cpus * cpus)
    val queue: BlockingQueue[Option[Future[B]]] = new ArrayBlockingQueue(1 + 16 * cpus)
    Future {
      try i.foreach(x => queue.put(Some(Future(f(x)))))
      finally queue.put(None) // poison
    }
    new Iterator[B] {

      private var fopt: Option[Future[B]] = None
      private var alive = true

      override def next() =
        if (hasNext) { val v = Await.result(fopt.get, Inf); fopt = None; v }
        else Iterator.empty.next()

      override def hasNext: Boolean = alive && take().isDefined

      private def take(): Option[Future[B]] = {
        if (fopt.isEmpty) {
          fopt = queue.take() match {
            case None => { alive = false; None }
            case some => some
          }
        }
        fopt
      }

    }
  }
}
