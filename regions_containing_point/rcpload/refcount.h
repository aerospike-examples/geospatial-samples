#ifndef refcount_h__
#define refcount_h__

// This reference counting design is based on Section 29 of "More
// Effective C++" by Scott Meyers.

class RefCountObj
{
public:
    /// Default constructor.
    RefCountObj() : m_refcount(0) {}

    /// Copy constructor.
    RefCountObj(RefCountObj const &) : m_refcount(0) {}

    /// Destructor.
    virtual ~RefCountObj() {}

    /// Assignment operator.
    RefCountObj& operator=(RefCountObj const &) { return *this; }

    /// Add a reference, returns refcount after increment.
	virtual int64_t rc_add_ref(void * ptr = NULL) const
    {
        return __sync_add_and_fetch(&m_refcount, 1);
    }

    /// Remove a reference, returns refcount after decrement.
	virtual int64_t rc_rem_ref(void * ptr = NULL) const
    {
        int64_t val = __sync_add_and_fetch(&m_refcount, -1);
        if (val == 0)
            delete this;
        return val;
    }

    /// Returns reference count.
    int64_t rc_count() const
    {
        return m_refcount;
    }

private:
    mutable volatile int64_t	m_refcount;
};

template <typename T>
class RefCountPtr
{
public:
    inline RefCountPtr(T* ptr = 0)
        : m_ptr(ptr)
    {
        initialize();
    }

    inline RefCountPtr(RefCountPtr const & rhs)
        : m_ptr(rhs.m_ptr)
    {
        initialize();
    }

    template <typename T2>
    friend class RefCountPtr;

    /// Converting Constructor
    template <typename S>
    RefCountPtr(RefCountPtr<S> const & cp)
        : m_ptr(cp.m_ptr)
    {
        initialize();
    }

    // Desctructor
    inline ~RefCountPtr()
    {
        terminate();
    }

    /// Assignment operator
    inline RefCountPtr& operator=(RefCountPtr const & rhs)
    {
        if (m_ptr != rhs.m_ptr)
        {
            terminate();
            m_ptr = rhs.m_ptr;
            initialize();
        }

        return *this;
    }

    /// Dereference (for member) operator, returns pointer.
    inline T* operator->() const
    {
        return m_ptr;
    }

    /// Dereference operator, returns reference.
    inline T& operator*() const
    {
        return *m_ptr;
    }

    /// Null predicate.
    inline bool operator!() const { return m_ptr == 0; }

    /// Non-Null predicate.
    inline operator bool() const { return m_ptr != 0; }

    /// Less-than predicate.
    inline bool operator<(RefCountPtr const & b) const
    {
        return *m_ptr < *b.m_ptr;
    }

    /// Less-than-or-equal predicate.
    inline bool operator<=(RefCountPtr const & b) const
    {
        return *m_ptr <= *b.m_ptr;
    }

    /// Greater-than predicate.
    inline bool operator>(RefCountPtr const & b) const
    {
        return *m_ptr > *b.m_ptr;
    }

    /// Greater-than-or-equal predicate.
    inline bool operator>=(RefCountPtr const & b) const
    {
        return *m_ptr >= *b.m_ptr;
    }

    /// Equals predicate.
    inline bool operator==(RefCountPtr const & b) const
    {
        return *m_ptr == *b.m_ptr;
    }

    /// Not-equals predicate.
    inline bool operator!=(RefCountPtr const & b) const
    {
        return *m_ptr != *b.m_ptr;
    }

    /// Strict equality predicate.
    inline bool same(RefCountPtr const & other) const
    {
        return m_ptr == other.m_ptr;
    }

private:
    inline void initialize()
    {
        if (m_ptr)
            m_ptr->rc_add_ref((void *) &m_ptr);
    }

    inline void terminate()
    {
        if (m_ptr)
            m_ptr->rc_rem_ref((void *) &m_ptr);
    }

    T *		m_ptr;
};

/// Delegate stream insertion to the referenced object.
template<typename T>
std::ostream & operator<<(std::ostream & s, RefCountPtr<T> const & p)
{
    s << *p.m_ptr;
    return s;
}

// Local Variables:
// mode: C++
// tab-width: 4
// c-basic-offset: 4
// End:

#endif // refcount_h__
