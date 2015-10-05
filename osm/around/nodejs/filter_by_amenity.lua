local function select_value(rec)
  -- info('%s', rec.val)
  return rec.val
end

function apply_filter(stream, amen)
  local function match_amenity(rec)
    return rec.map.amenity and rec.map.amenity == amen
  end
  return stream : filter(match_amenity) : map(select_value)
end
