local function select_value(rec)
  -- info('%s', rec.val)
  return rec.val
end

function apply_filter(stream, cat)
  local function contains_category(rec)
    for item in list.iterator(rec.map.categories) do
      if item == cat then
        return true
      end
    end
    return false
  end
  return stream : filter(contains_category) : map(select_value)
end
