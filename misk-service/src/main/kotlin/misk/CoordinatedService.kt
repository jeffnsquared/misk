package misk

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.Service.Listener
import com.google.common.util.concurrent.Service.State
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Provider

internal class CoordinatedService(
  private val serviceProvider: Provider<out Service>
) : AbstractService(), DelegatingService {

  override val service: Service by lazy {
    serviceProvider.get()
      .also { created ->
        created.checkNew("$created must be NEW for it to be coordinated")
        created.addListener(
          object : Listener() {

            val outerService = this@CoordinatedService

            override fun running() {
              outerService.notifyStarted()
              downstreamServices.forEach { it.startIfReady() }
            }

            override fun terminated(from: State) {
              outerService.notifyStopped()
              upstreamServices.forEach { it.stopIfReady() }
            }

            override fun failed(from: State, failure: Throwable) {
              outerService.notifyFailed(failure)
            }
          },
          MoreExecutors.directExecutor()
        )
      }
  }

  /** Services that start before this. */
  private val directDependsOn = mutableSetOf<CoordinatedService>()

  /** Services this starts before. */
  private val directDependencies = mutableSetOf<CoordinatedService>()

  /**
   * Services that enhance this. This starts before them, but they start before
   * [directDependencies].
   */
  private val enhancements = mutableSetOf<CoordinatedService>()

  /** Service that starts up before this, and whose [directDependencies] also depend on this. */
  private var enhancementTarget: CoordinatedService? = null

  /**
   * Used to track internally if this [CoordinatedService] has invoked [startAsync] on the inner
   * [Service] [service]
   * */
  private val innerServiceStarted = AtomicBoolean(false)

  /**
   * Returns a set of services that are required by this service.
   *
   * The set consists of the [enhancementTarget], all direct dependencies and each dependency's
   * transitive enhancements. It is the set of services that block start-up of this service.
   */
  val upstreamServices: Set<CoordinatedService> by lazy {
    val result = mutableSetOf<CoordinatedService>()
    if (enhancementTarget != null) {
      result += enhancementTarget!!
    }
    for (provider in directDependsOn) {
      result += provider
      provider.getTransitiveEnhancements(result)
    }
    result
  }

  private fun getTransitiveEnhancements(sink: MutableSet<CoordinatedService>) {
    sink += enhancements
    for (enhancement in enhancements) {
      enhancement.getTransitiveEnhancements(sink)
    }
  }

  /**
   * Returns a set of all services which require this service to be started before they can start,
   * and who must shut down before this service shuts down.
   *
   * The set contains this service's enhancements, its dependencies, and the dependencies of
   * [enhancementTarget] (if it exists) and all transitive targets.
   */
  val downstreamServices: Set<CoordinatedService> by lazy {
    val result = mutableSetOf<CoordinatedService>()
    result += enhancements
    var t: CoordinatedService? = this
    while (t != null) {
      result += t.directDependencies
      t = t.enhancementTarget
    }
    result
  }

  /** Adds [services] as dependents downstream. */
  fun addDependentServices(vararg services: CoordinatedService) {
    // Check that this service and all dependent services are new before modifying the graph.
    this.checkNew()
    for (service in services) {
      service.checkNew()
      directDependencies += service
      service.directDependsOn += this
    }
  }

  /**
   * Adds [services] as enhancements to this service. Enhancements will start after the coordinated
   * service is running, and stop before it stops.
   */
  fun addEnhancements(vararg services: CoordinatedService) {
    // Check that this service and all dependent services are new before modifying the graph.
    this.checkNew()
    for (service in services) {
      service.checkNew()
      enhancements += service
      service.enhancementTarget = this
    }
  }

  private fun isTerminated(): Boolean {
    return state() == State.TERMINATED
  }

  override fun doStart() {
    startIfReady()
  }

  private fun startIfReady() {
    val canStartInner = state() == State.STARTING && upstreamServices.all { it.isRunning() }

    // startAsync must be called exactly once
    if (canStartInner) {
      val started = innerServiceStarted.getAndSet(true)
      if (!started) {
        service.startAsync()
      }
    }
  }

  override fun doStop() {
    stopIfReady()
  }

  private fun stopIfReady() {
    val canStopInner =
      state() == State.STOPPING && downstreamServices.all { it.isTerminated() }

    // stopAsync can be called multiple times, with subsequent calls being ignored
    if (canStopInner) {
      service.stopAsync()
    }
  }

  override fun toString() = service.toString()

  /**
   * Traverses the dependency/enhancement graph to detect cycles. If a cycle is found, then a
   * list of services that forms this cycle is returned.
   *
   * @param validityMap: A map that is used to track traversal of this service's dependency graph.
   */
  fun findCycle(
    validityMap: MutableMap<CoordinatedService, CycleValidity>
  ): MutableList<CoordinatedService>? {
    when (validityMap[this]) {
      CycleValidity.NO_CYCLES -> return null // We checked this node already.
      CycleValidity.CHECKING_FOR_CYCLES -> return mutableListOf(this) // We found a cycle!
      else -> {
        validityMap[this] = CycleValidity.CHECKING_FOR_CYCLES
        // First check there are no cycles in the enhancements that could cause
        // getDownstreamServices() to get stuck.
        for (enhancement in enhancements) {
          val cycle = enhancement.findCycle(validityMap)
          if (cycle != null) {
            cycle.add(this)
            return cycle
          }
        }
        // Now check that there are no mixed enhancement-dependency cycles.
        for (dependency in downstreamServices) {
          val cycle = dependency.findCycle(validityMap)
          if (cycle != null) {
            cycle.add(this)
            return cycle
          }
        }
        validityMap[this] = CycleValidity.NO_CYCLES
        return null
      }
    }
  }

  companion object {
    /**
     * CycleValidity provides states used to track dependency graph traversal and cycle detection.
     */
    enum class CycleValidity {
      CHECKING_FOR_CYCLES,
      NO_CYCLES,
    }

    /**
     * Extension to check that a given [Service] is `NEW`.
     */
    private fun Service.checkNew(
      message: String = "Cannot add dependencies after the service graph has been built"
    ) {
      check(state() == State.NEW) { message }
    }
  }
}
