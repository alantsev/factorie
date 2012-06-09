package cc.factorie
import cc.factorie.generative.{GenerativeFactor,GenerativeModel}
import scala.collection.mutable.{ArrayBuffer,HashMap}

class GibbsSampler(val model:Model, val objective:Model = null) extends ProposalSampler[Variable] {
  val handlers = new ArrayBuffer[GibbsSamplerHandler]
  def defaultHandlers = List(GeneratedVarGibbsSamplerHandler) //, MixtureChoiceGibbsSamplerHandler, IterableSettingsGibbsSamplerHandler
  handlers ++= defaultHandlers
  val cacheClosures = true
  def closures = new HashMap[Variable, GibbsSamplerClosure]
  val doProcessByHandlers = model.isInstanceOf[GenerativeModel]
  override def process1(v:Variable): DiffList = if (doProcessByHandlers) processByHandlers(v) else processByProposals(v)
  def processByHandlers(v:Variable): DiffList = {
    val d = newDiffList
    // If we have a cached closure, just use it and return
    if (cacheClosures && closures.contains(v)) { closures(v).sample(d); return d }
    // Get factors, in sorted order of the their classname
    val factors = model.factors(v).toSeq.sortWith((f1:Factor,f2:Factor) => f1.getClass.getName < f2.getClass.getName).toSeq
    var done = false
    val handlerIterator = handlers.iterator
    while (!done && handlerIterator.hasNext) {
      val closure = handlerIterator.next.sampler(v, factors, this)
      if (closure ne null) {
        done = true
        closure.sample(d)
        if (cacheClosures) closures(v) = closure
      }
    }
    if (!done) throw new Error("GibbsSampler: No sampling handler found for "+factors)
    d
  }
  def processByProposals(v:Variable): DiffList = {
    val props = proposals(v)
    proposalsHook(props)
    val proposal = props.size match {
      case 0 => if (skipEmptyProposals) return newDiffList else throw new Error("No proposals created.")
      case 1 => props.head 
      case _ => pickProposal(props)
    }
    proposal.diff.redo
    proposalHook(proposal)
    proposal.diff
  }

  def proposals(v:Variable): Seq[Proposal] = model match {
    case m:GenerativeModel => throw new Error("Not yet implemented")
    case m:Model => v match {
      case v:DiscreteVariable => proposals(v)
      case v:Variable with IterableSettings => proposals(v.settings)
    } 
  }
  def proposals(si:SettingIterator): Seq[Proposal] = si.map(d => {val (m,o) = d.scoreAndUndo(model,objective); new Proposal(d, m, o, m/temperature)}).toSeq
  // Special case for a bit more efficiency 
  def proposals(dv:DiscreteVariable): Seq[Proposal] = {
    var i = 0; val len = dv.domain.size
    val result = new ArrayBuffer[Proposal](len)
    while (i < len) {
      val diff = new DiffList
      dv.set(i)(diff)
      val (modelScore, objectiveScore) = diff.scoreAndUndo(model, objective)
      result += new Proposal(diff, modelScore, objectiveScore, modelScore/temperature)
      i += 1
    }
    result
  }
}

trait GibbsSamplerHandler {
  def sampler(v:Variable, factors:Seq[Factor], sampler:GibbsSampler): GibbsSamplerClosure
}
trait GibbsSamplerClosure {
  def sample(implicit d:DiffList = null): Unit
}


object GeneratedVarGibbsSamplerHandler extends GibbsSamplerHandler {
  class Closure(val variable:MutableVar, val factor:GenerativeFactor) extends GibbsSamplerClosure {
    def sample(implicit d:DiffList = null): Unit = variable.set(factor.sampledValue.asInstanceOf[variable.Value])
  }
  def sampler(v:Variable, factors:Seq[Factor], sampler:GibbsSampler): GibbsSamplerClosure = {
    factors match {
      case List(factor:GenerativeFactor) => {
        v match {
          case v:MutableVar => new Closure(v, factor)
        }
      }
      case _ => null
    }
  }
}
